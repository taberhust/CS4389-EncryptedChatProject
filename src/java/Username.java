package java;

public class Username{
	private String name;
	private boolean taken;
	
	public Username(String name)
	{
		this.name = name;
		this.taken = false;
	}
	
	public String getName()
	{
		return this.name;
	}
	
	public void setName(String name)
	{
		this.name = name;
	}
	
	public boolean getTaken()
	{
		return this.taken;
	}
	
	public void setTaken()
	{
		this.taken = true;
	}
	
	public void setOpen()
	{
		this.taken = false;
	}
}