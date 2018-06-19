package com.asador.oanda.orders;

import java.util.Collection;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.asador.oanda.orders.domain.Order;

@RestController
@RequestMapping("/orders")
public class OrderController {
	
	@Autowired
	private OrderManager orderManager;

	@GetMapping(produces="application/json")
	@ResponseBody()
	public Collection<Order> listPendingOrders() {
		return orderManager.getPendingStopOrders();
	}
	
	@PostMapping()
	@ResponseStatus(HttpStatus.CREATED)
	public void createStopOrder(@RequestBody Order order) {
		orderManager.createStopOrder(order);
	}
	
	@DeleteMapping("/{orderId}")
	@ResponseStatus(HttpStatus.OK)
	public void cancelOrder(@PathVariable long orderId) {
		orderManager.cancelPendingStopOrder(orderId);
	}
}
