package com.asador.oanda.orders;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.asador.oanda.orders.domain.Order;

@Component
public class OrderManager {
	private final Logger logger = LoggerFactory.getLogger(OrderManager.class);
	
	private Map<String, Order> pendingOrders = new HashMap<>();

	public void createStopOrder(Order order) {
		order.setOrderId(System.currentTimeMillis()+"");
		pendingOrders.put(order.getOrderId(), order);
		
		logger.info("Order created {}", order.getOrderId());
	}
	
	public Collection<Order> getPendingStopOrders() {
		return Collections.unmodifiableCollection(pendingOrders.values());
	}
	
	public void cancelPendingOrder(String orderId) {
		pendingOrders.remove(orderId);
		logger.info("Order canceled {}", orderId);
	}
	
	private void loadOrders() {
		
	}
}
