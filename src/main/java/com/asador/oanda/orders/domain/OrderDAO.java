package com.asador.oanda.orders.domain;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class OrderDAO {
	private Map<String, Order> pendingOrders = new HashMap<>();

	public String createOrder(Order order) {
		String orderId = String.valueOf(System.currentTimeMillis());
		order.setOrderId(orderId);
		pendingOrders.put(order.getOrderId(), order);
		
		return orderId;
	}
	
	public Collection<Order> getOrders() {
		return Collections.unmodifiableCollection(pendingOrders.values());		
	}
	
	public void removeOrder(String orderId) {
		pendingOrders.remove(orderId);
	}

}
