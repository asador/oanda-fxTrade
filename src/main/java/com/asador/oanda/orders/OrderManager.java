package com.asador.oanda.orders;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.asador.oanda.orders.domain.Order;
import com.asador.oanda.orders.domain.OrderAction;
import com.asador.oanda.orders.domain.OrderDAO;
import com.oanda.v20.Context;
import com.oanda.v20.ContextBuilder;
import com.oanda.v20.ExecuteException;
import com.oanda.v20.RequestException;
import com.oanda.v20.account.AccountID;
import com.oanda.v20.instrument.Candlestick;
import com.oanda.v20.instrument.CandlestickGranularity;
import com.oanda.v20.instrument.InstrumentCandlesRequest;
import com.oanda.v20.instrument.InstrumentCandlesResponse;
import com.oanda.v20.order.OrderCreateRequest;
import com.oanda.v20.order.OrderCreateResponse;
import com.oanda.v20.order.StopOrderRequest;
import com.oanda.v20.order.TimeInForce;
import com.oanda.v20.primitives.DateTime;
import com.oanda.v20.primitives.InstrumentName;
import com.oanda.v20.transaction.OrderCancelTransaction;
import com.oanda.v20.transaction.StopLossDetails;
import com.oanda.v20.transaction.TakeProfitDetails;
import com.oanda.v20.transaction.Transaction;

@Component
public class OrderManager {
	private final Logger logger = LoggerFactory.getLogger(OrderManager.class);
	
	@Value("${oanda.accountId}")
	private String accountId;
	
	@Value("${oanda.accessToken}")
	private String accessToken;
	
	@Value("${oanda.restEndpoint}")
	private String oandaApiEndpoint;
	
	@Autowired
	private OrderDAO orderDao;
	
	private Context oandaCtx;
	private AccountID accountIdObject;
	
	private ExecutorService executor = Executors.newCachedThreadPool();
	private Set<Long> cancelledOrderIds = new HashSet<>();
	
	@PostConstruct
	protected void init() {
		oandaCtx = new ContextBuilder(oandaApiEndpoint)
				.setApplication("")
				.setToken(accessToken)
				.build();
		accountIdObject = new AccountID(accountId);
		
		for (Order order : orderDao.getOrders()) {
			createOrderWatch(order);
		}		
	}
	
	public long createStopOrder(Order order) {
		
		validateOrder(order);
		
		if (isOrderDuplicate(order))
			throw new RuntimeException("A similar order already exist for " + order.getAction() + " " + order.getInstrument());
		
		long orderId = orderDao.createOrder(order);
		logger.info("Pending order created. Order Id {}", orderId);
		
		createOrderWatch(order);
		
		return orderId;
	}
	
	void validateOrder(Order order) throws IllegalArgumentException {
		if (order.getInstrument() == null || order.getAction() == null || order.getUnits() <= 0 ||
				order.getStopEntry() <= 0 || order.getStopLoss() <= 0 || order.getTargetProfit() <= 0 ||
				order.getTriggerDistancePips() <= 0 )
			throw new IllegalArgumentException("One or more order attributes are wrong.");
		
		order.setInstrument(order.getInstrument().toUpperCase());
	}
	
	boolean isOrderDuplicate(Order newOrder) {
		for (Order existingOrder : orderDao.getOrders()) {
			if (newOrder.getInstrument().equals(existingOrder.getInstrument()) &&
					newOrder.getAction() == existingOrder.getAction())
				return true;
		}
		return false;
	}

	private void createOrderWatch(Order order) {
		executor.submit(() -> {
			watchPriceAndPlaceStopOrder(order);
		});
	}
	
	void watchPriceAndPlaceStopOrder(Order order) {
		logger.info("Start checking the price for {} to {} at {}. Order will be placed when price reaches {}", 
				order.getInstrument(),	order.getAction(), order.getStopEntry(), getOrderPlacementPrice(order));
		
		boolean priceReached = false;
		
		InstrumentCandlesRequest request = new InstrumentCandlesRequest(new InstrumentName(order.getInstrument()));
		request.setCount(1L);
		request.setPrice("M");
		request.setGranularity(CandlestickGranularity.M1);
		
		try {
			while (!priceReached && !isOrderCancelled(order.getOrderId())) {
				InstrumentCandlesResponse response = oandaCtx.instrument.candles(request);
				Candlestick mostRecentCandlestick = response.getCandles().get(0);
				if (priceMeetsOrderPlacementCondition(mostRecentCandlestick, order)) {
					logger.info("{} reached {}. It's time to place {} stop order at {}", order.getInstrument(),
							getOrderPlacementPrice(order), order.getAction(), order.getStopEntry());
					priceReached = true;
				}
				else
					Thread.sleep(1000);
			}
			if (!isOrderCancelled(order.getOrderId())) {				
				// price is in the zone, time to place the order
				placeStopOrder(order);
			} else {
				cancelledOrderIds.remove(order.getOrderId());
				logger.info("Stopped price watch for order {} {} {} as it was canceled", order.getOrderId(), 
						order.getAction(), order.getInstrument());
			}
			
		} catch (Exception e) {
			logger.error("Order " + order.getInstrument() + ", " + order.getAction() + " at " + 
					order.getStopEntry() + " was canceled due to exception.", e);
		} finally {				
			try {
				orderDao.removeOrder(order.getOrderId());
			} catch (Exception e) {
				// ignore it
			}
		}
		
	}
	
	private boolean isOrderCancelled(long orderId) {
		return cancelledOrderIds.contains(orderId);
	}
	
	boolean priceMeetsOrderPlacementCondition(Candlestick candlestick, Order order) {
		if (order.getAction() == OrderAction.BUY) {
			if (candlestick.getMid().getC().doubleValue() <= getOrderPlacementPrice(order))
				return true;
		} else {
			if (candlestick.getMid().getC().doubleValue() >= getOrderPlacementPrice(order))
				return true;	
		}
		return false;
	}
	
	double convertPip2PriceValue(int pip, String instrument) {
		if (instrument.contains("_JPY"))
			return (double)pip / 100;
		else 
			return (double)pip / 10000;
	}
	
	double getOrderPlacementPrice(Order order) {
		if (order.getAction() == OrderAction.BUY) {
			return order.getStopEntry() - convertPip2PriceValue(order.getTriggerDistancePips(), order.getInstrument());
		} else {
			return order.getStopEntry() + convertPip2PriceValue(order.getTriggerDistancePips(), order.getInstrument());
		}
	}
	
	void placeStopOrder(Order order) throws RequestException, ExecuteException {
        OrderCreateRequest request = new OrderCreateRequest(accountIdObject);

        StopOrderRequest stopOrder = new StopOrderRequest();
        stopOrder.setInstrument(order.getInstrument());
        stopOrder.setPrice(order.getStopEntry());
        stopOrder.setStopLossOnFill(new StopLossDetails().setPrice(order.getStopLoss()));
        stopOrder.setTakeProfitOnFill(new TakeProfitDetails().setPrice(order.getTargetProfit()));
        stopOrder.setTimeInForce(TimeInForce.GTD);
        stopOrder.setGtdTime(new DateTime(getNextWeekTimeInRFC3339()));
        if (order.getAction() == OrderAction.BUY)
        	stopOrder.setUnits(order.getUnits());
        else
        	stopOrder.setUnits(-order.getUnits());
        	        
        // Attach the body parameter to the request
        request.setOrder(stopOrder);
        
        OrderCreateResponse response = oandaCtx.order.create(request);
		Transaction transaction = response.getOrderCreateTransaction();
		logger.info("Created {} {} order with transaction ID {}", order.getInstrument(), order.getAction(), 
				transaction.getId());
		
		OrderCancelTransaction orderCancelTx = response.getOrderCancelTransaction();
		if (orderCancelTx != null) {
			logger.error("{} {} order immediately canceled due to {}", order.getInstrument(), order.getAction(), 
					orderCancelTx.getReason());
		}
	}
	
	/**
	 * Date format: YYYY-MM-DDTHH:MM:SS.nnnnnnnnnZ
	 * @return Next week date time in RFC3339
	 */
	static String getNextWeekTimeInRFC3339() {
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.DAY_OF_MONTH, 7);
		Date nextWeek = cal.getTime();
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
		return df.format(nextWeek);
	}
	
	public Collection<Order> getPendingStopOrders() {
		return orderDao.getOrders();
	}
	
	public void cancelPendingStopOrder(long orderId) {
		boolean orderRemoved = orderDao.removeOrder(orderId);
		if (orderRemoved) {
			cancelledOrderIds.add(orderId);			
			logger.info("Order {} canceled.", orderId);
		} else
			throw new RuntimeException("Order not found " + orderId);
	}
	
	// a hack to override oanda context with mocks during testing
	Context getOandaContext() {
		return oandaCtx;
	}
	
	public static void main(String[] a) {
		System.out.println(getNextWeekTimeInRFC3339());
	}
}
