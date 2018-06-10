package com.asador.oanda.orders.domain;

public class Order {

	private String orderId;
	private String instrument;
	private OrderAction action;
	private long units;
	private double stopEntry;
	private double targetProfit;
	private double stopLoss;
	private int triggerDistancePips;	//this must be a positive number
	
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
	public long getUnits() {
		return units;
	}
	public void setUnits(long units) {
		this.units = units;
	}
	public double getStopEntry() {
		return stopEntry;
	}
	public void setStopEntry(double stopEntry) {
		this.stopEntry = stopEntry;
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
	public int getTriggerDistancePips() {
		return triggerDistancePips;
	}
	public void setTriggerDistancePips(int triggerDistancePips) {
		this.triggerDistancePips = triggerDistancePips;
	}	
	
}
