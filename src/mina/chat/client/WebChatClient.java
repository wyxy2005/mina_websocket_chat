package mina.chat.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.Queue;

import javax.servlet.jsp.JspWriter;

import org.apache.mina.transport.socket.nio.NioSocketConnector;

import mina.chat.client.ChatClientHandler.Callback;

/**
 * @author wll27
 *
 */
public class WebChatClient implements Callback  {
	private Queue<String> queue;
    private ChatClientSupport client;
    public ChatClientSupport getClient() {
		return client;
	}
	private ChatClientHandler handler;
    private NioSocketConnector connector;
    
	public WebChatClient(){
		queue = new LinkedList<String>();
		connector = new NioSocketConnector();
		SocketAddress address = new InetSocketAddress("localhost", 1234);
        String name = "test";

        // Set Chat Application
        handler = new ChatClientHandler(WebChatClient.this);
        client = new ChatClientSupport(name, handler);

        // Connect Failed
        if (!client.connect(connector, address, false)) {
        	
        }
	}
	
	public WebChatClient(String address, String username){
		queue = new LinkedList<String>();
		connector = new NioSocketConnector();
		String[] tmp = address.split(":", 2);
		SocketAddress soAddress = new InetSocketAddress(tmp[0], Integer.parseInt(tmp[1]));

        // Set Chat Application
        handler = new ChatClientHandler(WebChatClient.this);
        client = new ChatClientSupport(username, handler);

        // Connect Failed
        if (!client.connect(connector, soAddress, false)) {
        	
        }
	}
	
	public boolean getQueueisEmpty(){
		return queue.isEmpty();
	}
	public String getMessagefromQueue(){
		
		return queue.remove();
	}
	@Override
	public void connected() {
		// TODO Auto-generated method stub
		
	}
	@Override
	public void loggedIn() {
		// TODO Auto-generated method stub
		queue.add("You have joined the Mina-chat.");
		
	}
	@Override
	public void loggedOut() {
		// TODO Auto-generated method stub
		queue.add("You have left the chat session.");
		
	}
	@Override
	public void disconnected() {
		// TODO Auto-generated method stub
		queue.add("Connection closed.");
	}
	@Override
	public void messageReceived(String message) {
		// TODO Auto-generated method stub
		queue.add(message);
	}
	@Override
	public void error(String message) {
		// TODO Auto-generated method stub
		queue.add(message);
	}
}
