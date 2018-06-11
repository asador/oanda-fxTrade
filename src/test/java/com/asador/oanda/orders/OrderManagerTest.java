package com.asador.oanda.orders;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
public class OrderManagerTest {

	@Test
	public void createStopOrder_WhenOrderInvalid_ShouldThrowException() {
		//TODO
	}
	
	@Test
	public void createStopOrder_WhenOrderDuplicate_ShouldThrowException() {
		//TODO
	}

	@Test
	public void createStopOrder_WhenNewOrder_ShouldCreatePendingOrderAndOrderWatch() {
		//TODO
	}
	
	@Test
	public void watchPriceAndPlaceStopOrder_WhenPriceReached_ShouldPlaceOandaStopOrderAndRemovePendingOrder() {
		
	}

	@Test
	public void watchPriceAndPlaceStopOrder_WhenOrderCanceled_ShouldStopOrderWatchAndRemovePendingOrder() {
		
	}

	@Test
	public void watchPriceAndPlaceStopOrder_WhenOandaException_ShouldStopOrderWatchAndRemovePendingOrder() {
		
	}

	@Test
	public void cancelPendingOrder_WhenInvalidOrderId_ShouldThrowOrderNotFoundException() {
		//TODO
	}

	@Test
	public void cancelPendingOrder_WhenValidOrderId_ShouldRemovePendingOrder() {
		//TODO
	}
	
	@Test
	public void getPendingStopOrders_ShouldReturnAllPendingOrders() {
		//TODO
	}

	@Test
	public void priceMeetsOrderPlacementCondition_WhenBuyOrderAndPriceAboveTriggerValue_ShouldReturnFalse() {
		//TODO
	}
	
	@Test
	public void priceMeetsOrderPlacementCondition_WhenBuyOrderAndPriceLessThanTriggerValue_ShouldReturnTrue() {
		//TODO
	}
	
	@Test
	public void priceMeetsOrderPlacementCondition_WhenBuyOrderAndPriceEqualsTriggerValue_ShouldReturnTrue() {
		//TODO
	}
	
	@Test
	public void priceMeetsOrderPlacementCondition_WhenSellOrderAndPriceMoreThanTriggerValue_ShouldReturnTrue() {
		//TODO
	}
	
	@Test
	public void priceMeetsOrderPlacementCondition_WhenSellOrderAndPriceBelowTriggerValue_ShouldReturnFalse() {
		//TODO
	}
	
	@Test
	public void priceMeetsOrderPlacementCondition_WhenSellOrderAndPriceEqualsTriggerValue_ShouldReturnTrue() {
		//TODO
	}

}
