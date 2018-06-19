package com.asador.oanda.orders.domain;

import java.sql.ResultSet;
import java.util.Collection;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class OrderDAO {
	
	private static final String SQL_INSERT_ORDER="insert into pending_order (order_id, instrument, action, units, stop_entry, target_profit, stop_loss, trigger_distance_pips) values (?,?,?,?,?,?,?,?)";
	private static final String SQL_SELECT_ALL_ORDERS="select * from pending_order";
	private static final String SQL_DELETE_ORDER="delete from pending_order where order_id=?";
	
	@Autowired
	private JdbcTemplate jdbcTemplate;

	public long createOrder(Order order) {
		long orderId = System.currentTimeMillis();
		order.setOrderId(orderId);
		jdbcTemplate.update(SQL_INSERT_ORDER, order.getOrderId(), order.getInstrument(), order.getAction().toString(), 
				order.getUnits(), order.getStopEntry(), order.getTargetProfit(), order.getStopLoss(), 
				order.getTriggerDistancePips());
		
		return orderId;
	}
	
	public Collection<Order> getOrders() {
		return jdbcTemplate.query(SQL_SELECT_ALL_ORDERS, getOrderRowMapper());
	}
	
	public void removeOrder(long orderId) {
		jdbcTemplate.update(SQL_DELETE_ORDER, orderId);
	}
	
	private RowMapper<Order> getOrderRowMapper() {
		return (ResultSet rs, int rowNum) -> {
			Order order = new Order();
			order.setOrderId(rs.getLong("order_id"));
			order.setInstrument(rs.getString("instrument"));
			order.setAction(OrderAction.valueOf(rs.getString("action")));
			order.setUnits(rs.getInt("units"));
			order.setStopEntry(rs.getDouble("stop_entry"));
			order.setTargetProfit(rs.getDouble("target_profit"));
			order.setStopLoss(rs.getDouble("stop_loss"));
			order.setTriggerDistancePips(rs.getInt("trigger_distance_pips"));
			return order;
		};
	}

}
