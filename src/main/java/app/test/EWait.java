package app.test;

public enum EWait
{
	Wait, NotWait;
	//--------------------------------
	public boolean isWait()
	{
		return this == Wait;
	}
}
