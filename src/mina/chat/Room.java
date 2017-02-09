package mina.chat;

import org.apache.mina.core.session.IoSession;

public class Room {
	private IoSession adminUser;
	private int size;
	private String title;
	private int roomID;
	
	public IoSession getAdminUser() {
		return adminUser;
	}
	public void setAdminUser(IoSession adminUser) {
		this.adminUser = adminUser;
	}
	public int getSize() {
		return size;
	}
	public void setSize(int size) {
		this.size = size;
	}
	public String getTitle() {
		return title;
	}
	public void setTitle(String title) {
		this.title = title;
	}
	public int getRoomID() {
		return roomID;
	}
	public void setRoomID(int roomID) {
		this.roomID = roomID;
	}
	public Room(IoSession session, int size, String title, int roomID){
		this.adminUser = session;
		this.size = size;
		this.title = title;
		this.roomID = roomID;
	}
	public Room(){
		this.adminUser = null;
		this.size = 100;
		this.title = "";
		this.roomID = 0;
	}
	
}
