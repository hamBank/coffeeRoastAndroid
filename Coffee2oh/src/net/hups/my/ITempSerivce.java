package net.hups.my;

public interface ITempSerivce {
	public void setCurrentTemprature(double inTemp);
	public void resetRunParams(); //endOfRun
	public void simluateMode(boolean simMode, int speedUp);
	public void setPower(int powerLevel);
	public void setRunMode(int runMode);		
	public void setMaxValue(int myinputTemp, boolean goesUp);
}
