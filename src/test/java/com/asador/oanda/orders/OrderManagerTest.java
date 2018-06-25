package com.asador.oanda.orders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collection;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import com.asador.oanda.orders.domain.Order;
import com.asador.oanda.orders.domain.OrderAction;
import com.asador.oanda.orders.domain.OrderDAO;
import com.oanda.v20.ExecuteException;
import com.oanda.v20.RequestException;
import com.oanda.v20.account.AccountID;
import com.oanda.v20.instrument.Candlestick;
import com.oanda.v20.instrument.CandlestickData;
import com.oanda.v20.instrument.InstrumentCandlesRequest;
import com.oanda.v20.instrument.InstrumentCandlesResponse;
import com.oanda.v20.instrument.InstrumentContext;
import com.oanda.v20.order.OrderContext;
import com.oanda.v20.order.OrderCreateRequest;
import com.oanda.v20.order.OrderCreateResponse;
import com.oanda.v20.primitives.DateTime;
import com.oanda.v20.transaction.RequestID;
import com.oanda.v20.transaction.Transaction;
import com.oanda.v20.transaction.TransactionID;
import com.oanda.v20.transaction.TransactionType;

@RunWith(SpringRunner.class)
@SpringBootTest
public class OrderManagerTest {
	
	@Autowired
	private OrderManager orderManager;
	
	@Autowired
	private OrderDAO orderDao;
	
	private OrderContext orderContextMock = mock(OrderContext.class);
	private InstrumentContext instrumentContextMock = mock(InstrumentContext.class);
	
	@Before
	public void setup() {
		orderManager.getOandaContext().instrument = instrumentContextMock;
		orderManager.getOandaContext().order = orderContextMock;
	}
	
	@After
	public void tearDown() {
		for (Order order: orderDao.getOrders())
			orderManager.cancelPendingStopOrder(order.getOrderId());
	}

	@Test
	public void validateOrder_WhenOrderInvalid_ShouldThrowException() {
		Order order = new Order();
		order.setInstrument("EUR_USD");
		order.setStopEntry(1.2345);	
		
		try {
			orderManager.validateOrder(order);
			Assert.fail("Validation had to fails when some fields are null");
		} catch (IllegalArgumentException e) {
			// everything is good
		}
	}
	
	@Test
	public void validateOrder_WhenOrderValid_ShouldReturn() {
		Order order = createEURUSDOrder();
		
		try {
			orderManager.validateOrder(order);
		} catch (IllegalArgumentException e) {
			Assert.fail("Validation had to pass when all required fields are set");
		}
	}

	private Order createEURUSDOrder() {
		Order order = new Order();
		order.setInstrument("EUR_USD");
		order.setAction(OrderAction.BUY);
		order.setStopEntry(1.0145);
		order.setTargetProfit(1.3245);
		order.setStopLoss(1.0033);
		order.setUnits(10000);
		order.setTriggerDistancePips(3);
		return order;
	}
	
	private Order createUSDCADOrder() {
		Order order = new Order();
		order.setInstrument("USD_CAD");
		order.setAction(OrderAction.SELL);
		order.setStopEntry(1.8345);
		order.setTargetProfit(1.5245);
		order.setStopLoss(1.8533);
		order.setUnits(10000);
		order.setTriggerDistancePips(3);
		return order;
	}
	
	@Test
	public void isOrderDuplicate_WhenOrderDuplicate_ShouldRetrunTrue() {
		orderDao.createOrder(createEURUSDOrder());
		Order anotherOrder = new Order();
		anotherOrder.setInstrument("EUR_USD");
		anotherOrder.setAction(OrderAction.BUY);
		anotherOrder.setStopEntry(0.8456);
		anotherOrder.setTargetProfit(1.1450);
		anotherOrder.setStopLoss(0.8256);
		anotherOrder.setUnits(10000);
		anotherOrder.setTriggerDistancePips(3);
	
		boolean duplicate = orderManager.isOrderDuplicate(anotherOrder);
		Assert.assertTrue("Duplicate order must be detected", duplicate);
	}
	
	@Test
	public void isOrderDuplicate_WhenOrderNotDuplicate_ShouldRetrunFalse() {
		orderDao.createOrder(createEURUSDOrder());
		Order anotherOrder = new Order();
		anotherOrder.setInstrument("USD_CAD");
		anotherOrder.setAction(OrderAction.BUY);
		anotherOrder.setStopEntry(1.1145);
		anotherOrder.setTargetProfit(1.1450);
		anotherOrder.setStopLoss(1.1033);
		anotherOrder.setUnits(10000);
		anotherOrder.setTriggerDistancePips(3);
	
		boolean duplicate = orderManager.isOrderDuplicate(anotherOrder);
		Assert.assertFalse("Not duplicate order must be detected", duplicate);
	}

	@Test
	public void createStopOrder_WhenNewOrder_ShouldCreatePendingOrderAndOrderWatch() {
		long orderId = orderManager.createStopOrder(createEURUSDOrder());
		
		Order order = orderDao.getOrder(orderId);
		Assert.assertNotNull("Order must be existed in the system", order);
	}
	
	@Test
	public void watchPriceAndPlaceStopOrder_WhenPriceReached_ShouldPlaceOandaStopOrderAndRemovePendingOrder() {
		InstrumentCandlesResponse instrumentCandleResponse1 = mock(InstrumentCandlesResponse.class);
		Candlestick candlestick1 = new Candlestick();
		CandlestickData candleData1 = new CandlestickData();
		candlestick1.setMid(candleData1);
		candleData1.setC(1.0152);
		when(instrumentCandleResponse1.getCandles()).thenReturn(Arrays.asList(candlestick1));

		InstrumentCandlesResponse instrumentCandleResponse2 = mock(InstrumentCandlesResponse.class);
		Candlestick candlestick2 = new Candlestick();
		CandlestickData candleData2 = new CandlestickData();
		candlestick2.setMid(candleData2);
		candleData2.setC(1.0140);
		when(instrumentCandleResponse2.getCandles()).thenReturn(Arrays.asList(candlestick2));
		
		OrderCreateResponse orderResponse = mock(OrderCreateResponse.class);
		when(orderResponse.getOrderCancelTransaction()).thenReturn(null);
		when(orderResponse.getOrderCreateTransaction()).thenReturn(createDummyTransaction());
		try {
			when(instrumentContextMock.candles(any(InstrumentCandlesRequest.class))).thenReturn(instrumentCandleResponse1).thenReturn(instrumentCandleResponse2);
			when(orderContextMock.create(any(OrderCreateRequest.class))).thenReturn(orderResponse);
		} catch (RequestException | ExecuteException e) {
			e.printStackTrace();
		}
		
		Order eurusd = createEURUSDOrder();
		orderDao.createOrder(eurusd);
		
		// call method under test
		orderManager.watchPriceAndPlaceStopOrder(eurusd);
		
		// check results
		Assert.assertNull("Pending order must have been removed once Oanda Stop order is placed.", orderDao.getOrder(eurusd.getOrderId()));
		
		try {
			verify(instrumentContextMock, times(2)).candles(any(InstrumentCandlesRequest.class));
			verify(orderContextMock).create(any(OrderCreateRequest.class));
		} catch (RequestException | ExecuteException e) {
			e.printStackTrace();
		}

	}

	@Test
	public void watchPriceAndPlaceStopOrder_WhenOrderCanceled_ShouldStopOrderWatchAndRemovePendingOrder() {
		InstrumentCandlesResponse instrumentCandleResponse1 = mock(InstrumentCandlesResponse.class);
		Candlestick candlestick1 = new Candlestick();
		CandlestickData candleData1 = new CandlestickData();
		candlestick1.setMid(candleData1);
		candleData1.setC(1.0252);
		when(instrumentCandleResponse1.getCandles()).thenReturn(Arrays.asList(candlestick1));

		try {
			when(instrumentContextMock.candles(any(InstrumentCandlesRequest.class))).thenReturn(instrumentCandleResponse1);
		} catch (RequestException | ExecuteException e) {
			e.printStackTrace();
		}
		
		Order eurusd = createEURUSDOrder();
		orderDao.createOrder(eurusd);
		
		new Thread(() -> {
			try {
				Thread.sleep(3000);
			} catch (InterruptedException e) {}
			
			orderManager.cancelPendingStopOrder(eurusd.getOrderId());
		}).start();
		
		// call method under test
		orderManager.watchPriceAndPlaceStopOrder(eurusd);
		
		// check results
		Assert.assertNull("Pending order must have been removed if it is cancelled.", orderDao.getOrder(eurusd.getOrderId()));
		
		try {
			verify(instrumentContextMock, atLeast(1)).candles(any(InstrumentCandlesRequest.class));
		} catch (RequestException | ExecuteException e) {
			e.printStackTrace();
		}
		
	}

	@Test
	public void watchPriceAndPlaceStopOrder_WhenOandaException_ShouldStopOrderWatchAndRemovePendingOrder() {
		try {
			when(instrumentContextMock.candles(any(InstrumentCandlesRequest.class))).thenThrow(new ExecuteException(new Exception()));
		} catch (RequestException | ExecuteException e) {
			e.printStackTrace();
		}
		
		Order eurusd = createEURUSDOrder();
		orderDao.createOrder(eurusd);
		
		// call method under test
		orderManager.watchPriceAndPlaceStopOrder(eurusd);
		
		// check results
		Assert.assertNull("Pending order must have been removed if Oanda exception happens.", orderDao.getOrder(eurusd.getOrderId()));
		
		try {
			verify(instrumentContextMock, times(1)).candles(any(InstrumentCandlesRequest.class));
		} catch (RequestException | ExecuteException e) {
			e.printStackTrace();
		}
		
	}

	@Test
	public void cancelPendingOrder_WhenInvalidOrderId_ShouldThrowOrderNotFoundException() {
		try {
			orderManager.cancelPendingStopOrder(123456);
			Assert.fail("Had to throw exception when order did not exist");
		} catch (RuntimeException e) {
			// all good
		}
	}

	@Test
	public void cancelPendingOrder_WhenValidOrderId_ShouldRemovePendingOrder() {
		long orderId = orderManager.createStopOrder(createEURUSDOrder());
		try {
			orderManager.cancelPendingStopOrder(orderId);
			
			Order theOrder = orderDao.getOrder(orderId);
			Assert.assertNull("Order must be removed.", theOrder);
		} catch (RuntimeException e) {
			Assert.fail(e.getMessage());
			e.printStackTrace();
		}
	}
	
	@Test
	public void getPendingStopOrders_ShouldReturnAllPendingOrders() {
		Order eurusd = createEURUSDOrder();
		orderDao.createOrder(eurusd);
		Order usdcad = createUSDCADOrder();
		orderDao.createOrder(usdcad);
		
		Collection<Order> allOrders = orderManager.getPendingStopOrders();
		Assert.assertEquals("Incorrect number of pending orders", 2, allOrders.size());		
	}

	@Test
	public void priceMeetsOrderPlacementCondition_WhenBuyOrderAndCurrentPriceAboveTriggerValue_ShouldReturnFalse() {
		Order order = new Order();
		order.setInstrument("GBP_USD");
		order.setAction(OrderAction.BUY);
		order.setStopEntry(1.2345);
		order.setTriggerDistancePips(3);
		
		Candlestick candlestick = new Candlestick();
		CandlestickData candleData = new CandlestickData();
		candlestick.setMid(candleData);
		candleData.setC(1.3256);
		
		boolean result = orderManager.priceMeetsOrderPlacementCondition(candlestick, order);
		Assert.assertFalse("Current price is still above triggering value and doesn't meet order placement criteria", result);
	}
	
	@Test
	public void priceMeetsOrderPlacementCondition_WhenBuyOrderAndCurrentPriceLessThanTriggerValue_ShouldReturnTrue() {
		Order order = new Order();
		order.setInstrument("GBP_USD");
		order.setAction(OrderAction.BUY);
		order.setStopEntry(1.2345);
		order.setTriggerDistancePips(3);
		
		Candlestick candlestick = new Candlestick();
		CandlestickData candleData = new CandlestickData();
		candlestick.setMid(candleData);
		candleData.setC(1.2335);
		
		boolean result = orderManager.priceMeetsOrderPlacementCondition(candlestick, order);
		Assert.assertTrue("Current price is less than triggering value and meets order placement criteria", result);
	}
	
	@Test
	public void priceMeetsOrderPlacementCondition_WhenBuyOrderAndCurrentPriceEqualsTriggerValue_ShouldReturnTrue() {
		Order order = new Order();
		order.setInstrument("GBP_USD");
		order.setAction(OrderAction.BUY);
		order.setStopEntry(1.2345);
		order.setTriggerDistancePips(3);
		
		Candlestick candlestick = new Candlestick();
		CandlestickData candleData = new CandlestickData();
		candlestick.setMid(candleData);
		candleData.setC(1.2342);
		
		boolean result = orderManager.priceMeetsOrderPlacementCondition(candlestick, order);
		Assert.assertTrue("Current price is equal to triggering value and meets order placement criteria", result);
	}
	
	@Test
	public void priceMeetsOrderPlacementCondition_WhenSellOrderAndCurrentPriceMoreThanTriggerValue_ShouldReturnTrue() {
		Order order = new Order();
		order.setInstrument("GBP_USD");
		order.setAction(OrderAction.SELL);
		order.setStopEntry(1.2345);
		order.setTriggerDistancePips(3);
		
		Candlestick candlestick = new Candlestick();
		CandlestickData candleData = new CandlestickData();
		candlestick.setMid(candleData);
		candleData.setC(1.2352);
		
		boolean result = orderManager.priceMeetsOrderPlacementCondition(candlestick, order);
		Assert.assertTrue("Current price is above triggering value and meets order placement criteria", result);
	}
	
	@Test
	public void priceMeetsOrderPlacementCondition_WhenSellOrderAndCurrentPriceBelowTriggerValue_ShouldReturnFalse() {
		Order order = new Order();
		order.setInstrument("GBP_USD");
		order.setAction(OrderAction.SELL);
		order.setStopEntry(1.2345);
		order.setTriggerDistancePips(3);
		
		Candlestick candlestick = new Candlestick();
		CandlestickData candleData = new CandlestickData();
		candlestick.setMid(candleData);
		candleData.setC(1.2145);
		
		boolean result = orderManager.priceMeetsOrderPlacementCondition(candlestick, order);
		Assert.assertFalse("Current price is below triggering value and doesn't meet order placement criteria", result);
	}
	
	@Test
	public void priceMeetsOrderPlacementCondition_WhenSellOrderAndCurrentPriceEqualsTriggerValue_ShouldReturnTrue() {
		Order order = new Order();
		order.setInstrument("GBP_USD");
		order.setAction(OrderAction.SELL);
		order.setStopEntry(1.2345);
		order.setTriggerDistancePips(3);
		
		Candlestick candlestick = new Candlestick();
		CandlestickData candleData = new CandlestickData();
		candlestick.setMid(candleData);
		candleData.setC(1.2348);
		
		boolean result = orderManager.priceMeetsOrderPlacementCondition(candlestick, order);
		Assert.assertTrue("Current price is equal to triggering value and meets order placement criteria", result);
	}

	@Test
	public void convertPip2PriceValue_WhenJPY_ShouldDivideBy100() {
		double result = orderManager.convertPip2PriceValue(5, "USD_JPY");
		Assert.assertEquals("Pip value should have been divided by 100", 0.05, result, 0);
	}
	
	@Test
	public void convertPip2PriceValue_WhenNonJPY_ShouldDivideBy10000() {
		double result = orderManager.convertPip2PriceValue(5, "USD_CAD");
		Assert.assertEquals("Pip value should have been divided by 10000", 0.0005, result, 0);
	}
	
	@Test
	public void getOrderPlacementPrice_WhenBuyOrder_ShouldSubtractTriggerPipsFromStopPrice() {
		Order order = new Order();
		order.setInstrument("GBP_USD");
		order.setAction(OrderAction.BUY);
		order.setStopEntry(1.2345);
		order.setTriggerDistancePips(5);
		
		double result = orderManager.getOrderPlacementPrice(order);
		Assert.assertEquals("Order placement price is wrong", 1.2340, result, 0.000001);
	}
	
	@Test
	public void getOrderPlacementPrice_WhenSellOrder_ShouldAddTriggerPipsToStopPrice() {
		Order order = new Order();
		order.setInstrument("GBP_USD");
		order.setAction(OrderAction.SELL);
		order.setStopEntry(1.2345);
		order.setTriggerDistancePips(5);
		
		double result = orderManager.getOrderPlacementPrice(order);
		Assert.assertEquals("Order placement price is wrong", 1.2350, result, 0.000001);		
	}

	private Transaction createDummyTransaction() {
		return new Transaction() {
			
			@Override
			public Transaction setUserID(Long userID) {
				// TODO Auto-generated method stub
				return null;
			}
			
			@Override
			public Transaction setTime(String time) {
				// TODO Auto-generated method stub
				return null;
			}
			
			@Override
			public Transaction setTime(DateTime time) {
				// TODO Auto-generated method stub
				return null;
			}
			
			@Override
			public Transaction setRequestID(String requestID) {
				// TODO Auto-generated method stub
				return null;
			}
			
			@Override
			public Transaction setRequestID(RequestID requestID) {
				// TODO Auto-generated method stub
				return null;
			}
			
			@Override
			public Transaction setId(String id) {
				// TODO Auto-generated method stub
				return null;
			}
			
			@Override
			public Transaction setId(TransactionID id) {
				// TODO Auto-generated method stub
				return null;
			}
			
			@Override
			public Transaction setBatchID(String batchID) {
				// TODO Auto-generated method stub
				return null;
			}
			
			@Override
			public Transaction setBatchID(TransactionID batchID) {
				// TODO Auto-generated method stub
				return null;
			}
			
			@Override
			public Transaction setAccountID(String accountID) {
				// TODO Auto-generated method stub
				return null;
			}
			
			@Override
			public Transaction setAccountID(AccountID accountID) {
				// TODO Auto-generated method stub
				return null;
			}
			
			@Override
			public Long getUserID() {
				// TODO Auto-generated method stub
				return null;
			}
			
			@Override
			public TransactionType getType() {
				// TODO Auto-generated method stub
				return null;
			}
			
			@Override
			public DateTime getTime() {
				// TODO Auto-generated method stub
				return null;
			}
			
			@Override
			public RequestID getRequestID() {
				// TODO Auto-generated method stub
				return null;
			}
			
			@Override
			public TransactionID getId() {
				// TODO Auto-generated method stub
				return null;
			}
			
			@Override
			public TransactionID getBatchID() {
				// TODO Auto-generated method stub
				return null;
			}
			
			@Override
			public AccountID getAccountID() {
				// TODO Auto-generated method stub
				return null;
			}
		};
	}
}
