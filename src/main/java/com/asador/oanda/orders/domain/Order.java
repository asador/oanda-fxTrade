package com.asador.oanda.orders.domain;

public class Order {

	private String orderId;
	private String instrument;
	private OrderAction action;
	private int unit;
	private double entry;
	private double targetProfit;
	private double stopLoss;
	
	public String getOrderId() {
		return orderId;
	}
	public void setOrderId(String orderId) {
		this.orderId = orderId;
	}
	public String getInstrument() {
		return instrument;
	}
	public void setInstrument(String instrument) {
		this.instrument = instrument;
	}
	public OrderAction getAction() {
		return action;
	}
	public void setAction(OrderAction action) {
		this.action = action;
	}
	public int getUnit() {
		return unit;
	}
	public void setUnit(int unit) {
		this.unit = unit;
	}
	public double getEntry() {
		return entry;
	}
	public void setEntry(double entry) {
		this.entry = entry;
	}
	public double getTargetProfit() {
		return targetProfit;
	}
	public void setTargetProfit(double targetProfit) {
		this.targetProfit = targetProfit;
	}
	public double getStopLoss() {
		return stopLoss;
	}
	public void setStopLoss(double stopLoss) {
		this.stopLoss = stopLoss;
	}
	
	
}
