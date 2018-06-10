package com.asador.oanda.orders;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
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
import com.oanda.v20.order.OrderCreateRequest;
import com.oanda.v20.order.OrderCreateResponse;
import com.oanda.v20.order.StopOrderRequest;
import com.oanda.v20.order.TimeInForce;
import com.oanda.v20.pricing.PricingCandlesRequest;
import com.oanda.v20.pricing.PricingCandlesResponse;
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
		
		logger.info("accountId: {}", accountId);
		logger.info("accessToken: {}", accessToken);
		logger.info("endpoint {}", oandaApiEndpoint);
	}
	
	public String createStopOrder(Order order) {
		
		// validate order
		validateOrder(order);
		
		// check for duplicate
		
		String orderId = orderDao.createOrder(order);
		logger.info("Pending order created {}", orderId);
		
		createOrderWatch(order);
		
		return orderId;
	}
	
	void validateOrder(Order order) throws IllegalArgumentException {
		
	}

	private void createOrderWatch(Order order) {
		executor.submit(() -> {
			watchPriceAndPlaceStopOrder(order);
		});
	}
	
	void watchPriceAndPlaceStopOrder(Order order) {
		boolean priceReached = false;
		
		PricingCandlesRequest request = new PricingCandlesRequest(new InstrumentName(order.getInstrument()));
		request.setCount(1L);
		request.setPrice("M");
		request.setGranularity(CandlestickGranularity.M1);
		
		try {
			while (!priceReached) {
				PricingCandlesResponse response = oandaCtx.pricing.candles(request);
				Candlestick mostRecentCandlestick = response.getCandles().get(0);
				if (priceMeetsOrderPlacementCondition(mostRecentCandlestick, order))
					priceReached = true;
				else
					Thread.sleep(1000);
			}
			// price is in the zone, time to place the order
			placeStopOrder(order);
			
		} catch (Exception e) {
			logger.error("Order " + order.getInstrument() + ", " + order.getAction() + " at " + 
					order.getStopEntry() + " was canceled due to exception.", e);
		} finally {				
			orderDao.removeOrder(order.getOrderId());
		}
		
	}
	
	boolean priceMeetsOrderPlacementCondition(Candlestick candlestick, Order order) {
		if (order.getAction() == OrderAction.BUY) {
			if (candlestick.getMid().getC().doubleValue() <= order.getStopEntry() - order.getTriggerDistancePips())
				return true;
		} else {
			if (candlestick.getMid().getC().doubleValue() >= order.getStopEntry() + order.getTriggerDistancePips())
				return true;	
		}
		return false;
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
		logger.info("Created {} {} order with transaction ID {}, type {} ", order.getInstrument(), order.getAction(), 
				transaction.getId(), transaction.getType());
		
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
	
	public void cancelPendingStopOrder(String orderId) {
		orderDao.removeOrder(orderId);
		logger.info("Order canceled {}", orderId);
	}
	
	public static void main(String[] a) {
		System.out.println(getNextWeekTimeInRFC3339());
	}
}
