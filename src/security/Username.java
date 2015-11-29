package security;

////////////////////////////////////////////////
//File:    Server.java
//Name:    Taber Hust, Michael Munzing, Brad Kupka
//Class:   CS 4389
//Date:    11/22/2015
//
//Final Project
////////////////////////////////////////////////

public class Username
{
	private String name;
	private boolean available;
	
	public Username(String name)
	{
		this.name = name;
		this.available = true;
	}
	
	public String getName()
	{
		return this.name;
	}
	
	public void setName(String name)
	{
		this.name = name;
	}
	
	public boolean isAvailable()
	{
		return this.available;
	}
	
	public void resetAvailable()
	{
		this.available = true;
	}
	
	public void setNotAvailable()
	{
		this.available = false;
	}
}