/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */
package mina.chat;

import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.SessionTrackingMode;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.future.WriteFuture;
import org.apache.mina.core.service.IoHandler;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolEncoderOutput;
import org.apache.mina.filter.logging.MdcInjectionFilter;
import org.apache.mina.proxy.utils.IoBufferDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import mina.chat.filter.WebSocketCodecPacket;
import mina.chat.filter.WebSocketEncoder;

/**
 * {@link IoHandler} implementation of a simple chat server protocol.
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public class ChatProtocolHandler extends IoHandlerAdapter {
	private final static Logger LOGGER = LoggerFactory.getLogger(ChatProtocolHandler.class);
	private static final CharsetEncoder ENCODER = Charset.forName("UTF-8").newEncoder();
	private int roomIdSeq = 0;
	// Session Array
	private final Set<IoSession> sessions = Collections.synchronizedSet(new HashSet<IoSession>());

	// Name Array to Identify Session
	private final Set<String> users = Collections.synchronizedSet(new HashSet<String>());

	private final Set<Room> rooms = Collections.synchronizedSet(new HashSet<Room>());

	public ChatProtocolHandler() {
		// TODO Auto-generated constructor stub
		rooms.add(new Room());
		/*rooms.add(new Room(null, 10, "Room No.1", ++roomIdSeq));
		rooms.add(new Room(null, 10, "Room No.2", ++roomIdSeq));
		rooms.add(new Room(null, 10, "Room No.3", ++roomIdSeq));
		rooms.add(new Room(null, 10, "Room No.4", ++roomIdSeq));*/
	}

	@Override
	public void exceptionCaught(IoSession session, Throwable cause) {
		LOGGER.warn("Unexpected exception.", cause);
		session.closeNow();
	}

	@Override
	public void messageReceived(IoSession session, Object message) {
		Logger log = LoggerFactory.getLogger(ChatProtocolHandler.class);
		log.info("received: " + message);
		String theMessage;

		IoBuffer buffer = (IoBuffer) message;
		log.info("buffer: " + buffer);
		byte[] bt = buffer.array();
		log.info("bt: " + bt.toString());
		theMessage = new String(bt);
		log.info("theMessage: " + theMessage);
		String result[] = theMessage.split(" ", 2);
		log.info("result: " + result);
		String theCommand = result[0];
		log.info("theCommand: " + theCommand);

		try {
			ChatCommand command = ChatCommand.valueOf(theCommand);
			String user = (String) session.getAttribute("user");

			switch (command.toInt()) {

			case ChatCommand.QUIT:
				//session.write("QUIT OK");
				refreshUsers("0");
				session.closeNow();
				break;
			case ChatCommand.LOGIN:

				if (user != null) {
					session.write("LOGIN ERROR user " + user + " already logged in.");
					return;
				}

				if (result.length == 2) {
					user = result[1];
				} else {
					session.write("LOGIN ERROR invalid login command.");
					return;
				}

				if (users.contains(user)) {
					session.write("LOGIN ERROR the name " + user + " is already used.");
					return;
				}

				sessions.add(session);

				session.setAttribute("user", user);
				MdcInjectionFilter.setProperty(session, "user", user);
				users.add(user);

				session.setAttribute("roomId", "0");
				MdcInjectionFilter.setProperty(session, "roomId", "0");

				//getUserList(session);
				
				session.write(str2Packet("LOGIN OK " + printRooms()));
				session.write(str2Packet("SETTITLE OK LOBBY"));
				refreshUsers("0");
				break;

			case ChatCommand.BROADCAST:
				if (result.length == 2) {
					String roomId = session.getAttribute("roomId").toString();
					roomBroadcast(user + ": " + result[1], roomId);
				}
				break;
			case ChatCommand.JOIN: // Written By HJ
				int getId = Integer.parseInt(result[1]);
				int roomSize = 0;
				Room roomGet = null;
				synchronized (rooms) {
					for(Room roomTmp : rooms){
						if (roomTmp.getRoomID() == getId) {
							roomGet = roomTmp;
							roomSize = roomTmp.getSize();
							break;
						}
					}
				}
				if (result.length == 2 && getRoomSessionCnt(roomGet) < roomSize) {
					setRoomid(session, result[1]);
					session.write(str2Packet("JOIN OK"));
					if(roomGet.getAdminUser().equals(session))
						session.write(str2Packet("CREATE OK "+roomGet.getRoomID()));
					roomBroadcast("The user " + user + " has joined this Room.", result[1]);
					refreshUsers(result[1]);
					refreshUsers("0");
					refreshRoom();
				} else {
					session.write(str2Packet("JOIN FAIL " + printRooms()));
				}
				// HJ End
				// Written By ESP
				Room currentRoom = getRoomByRoomId(result[1]);
				String crTitle = currentRoom.getTitle();
				session.write(str2Packet("SETTITLE OK " + crTitle));
				// ESP End
				break;
			case ChatCommand.CREATE:
				if (result.length == 2) {
					String[] roomArg = result[1].split(" ", 2);
					Room room = new Room(session, Integer.parseInt(roomArg[1]), roomArg[0], ++roomIdSeq);
					rooms.add(room);
					setRoomid(session, String.valueOf(roomIdSeq));
					session.write(str2Packet("JOIN OK "));
					session.write(str2Packet("CREATE OK "+roomIdSeq));
					session.write(str2Packet("SETTITLE OK " + roomArg[0]));
					roomBroadcast("The user " + user + " has joined this Room.", String.valueOf(roomIdSeq));
					refreshUsers(String.valueOf(roomIdSeq));
					refreshUsers("0");
					refreshRoom();
				}
				break;
			case ChatCommand.LEAVE:
				String roomId = session.getAttribute("roomId").toString();
				setRoomid(session, "0");
				roomBroadcast("The user " + user + " has left this Room.", roomId);
				refreshUsers(roomId);
				refreshUsers("0");
				refreshRoom();
				session.write(str2Packet("SETTITLE OK LOBBY"));
				break;
			case ChatCommand.DESTROY: // Written By ESP
				String id = session.getAttribute("roomId").toString();
				synchronized (sessions) {
					for (IoSession sessionTmp : sessions) {
						if (sessionTmp.isConnected() && (id.equals(sessionTmp.getAttribute("roomId").toString()))) {
							setRoomid(sessionTmp, "0");
						}		
					}
				}
				Room room = null;
				synchronized (rooms) {
					for(Room roomTmp : rooms){
						if(id.equals(String.valueOf(roomTmp.getRoomID()))){
							room = roomTmp;
							break;
						}
					}
				}
				rooms.remove(room);
				refreshRoom();
				refreshUsers("0");
				session.write(str2Packet("SETTITLE OK LOBBY"));
				break;
			case ChatCommand.USERKICK:
				String userk = result[1];
				String roomk= session.getAttribute("roomId").toString();
				IoSession roomAdminSession = getRoomByRoomId(roomk).getAdminUser();
				if(session.equals(roomAdminSession) && !session.equals(getSessionByUserName(userk))){
					IoSession kickUser = getSessionByUserName(userk);
					setRoomid(kickUser, "0");
					roomBroadcast("The user " + user + " Kicked.", roomk);
					refreshUsers(roomk);
					refreshUsers("0");
					refreshRoom();
					session.write(str2Packet("SETTITLE OK LOBBY"));
				}else{
					session.write(str2Packet("USERKICK FAIL"));
				}
				break;
			default:
				LOGGER.info("Unhandled command: " + command);
				break;
			}

		} catch (IllegalArgumentException e) {
			LOGGER.debug("Illegal argument", e);
		}
	}
	@Override
	public void sessionClosed(IoSession session) throws Exception {
		String user = (String) session.getAttribute("user");
		String roomId = session.getAttribute("roomId").toString();
		users.remove(user);
		sessions.remove(session);
		if(!roomId.equals("0"))
			roomBroadcast("The user " + user + " has left this Room.", roomId);
	}
	public void unicast(IoSession session, String message) {
		session.write("BROADCAST OK " + message);
	}
	public void refreshRoom(){
		synchronized (sessions) {
			for (IoSession sessionTmp : sessions) {
				// Session Write to Every Sessions
				if (sessionTmp.isConnected() && ("0".equals(sessionTmp.getAttribute("roomId").toString()))) {

					sessionTmp.write(str2Packet("LEAVE OK " + printRooms()));
				}
			}
		}
	}
	public WebSocketCodecPacket str2Packet(String str) {
		IoBuffer buf = IoBuffer.allocate(str.length()).setAutoExpand(true);

		try {
			buf.putString(str, ENCODER);
		} catch (CharacterCodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		buf.flip();
		return WebSocketCodecPacket.buildPacket(buf);
	}


	public void roomBroadcast(String message, String roomId) {
		synchronized (sessions) {
			for (IoSession sessionTmp : sessions) {
				// Session Write to Every Sessions
				if (sessionTmp.isConnected() && (roomId.equals(sessionTmp.getAttribute("roomId").toString()))) {
					sessionTmp.write(str2Packet("BROADCAST OK " + message));
				}
			}
		}
	}

	public void broadcast(String message) {
		synchronized (sessions) {
			for (IoSession sessionTmp : sessions) {
	
				if (sessionTmp.isConnected()) {
					sessionTmp.write("BROADCAST OK " + message);
				}
			}
		}
	}

	public int getRoomSessionCnt(Room room) {
		int cnt = 0;
		int roomId = room.getRoomID();
		synchronized (sessions) {
			for (IoSession session : sessions) {
				if (roomId == Integer.parseInt((String) session.getAttribute("roomId"))) {
					cnt++;
				}
			}
		}
		return cnt;
	}

	public void setRoomid(IoSession session, String roomId) {
		session.setAttribute("roomId", roomId);
		MdcInjectionFilter.setProperty(session, "roomId", roomId);
	}

	public String printRooms() {
		String initMsg = "<table id='roomList'>";
		synchronized (rooms) {
			for (Room roomTmp : rooms) {
				if (roomTmp.getRoomID() != 0) {
					initMsg += "<tr id='room" + roomTmp.getRoomID() + "' onclick='roomJoin(" + roomTmp.getRoomID()
							+ ")'>" + "<td class='roomTitle'>" + roomTmp.getTitle() + "</td>" + "<td class='roomSize'>"
							+ getRoomSessionCnt(roomTmp) + "/" + roomTmp.getSize() + "</td>" + "</tr>";
				}
			}
		}
		initMsg += "</table>";
		return initMsg;
	}
	public void refreshUsers(String roomId){
		String usrTable = "<table id='users'>";
		String usrTableAdmin = "<table id='users'>";
		IoSession adminSession = getRoomByRoomId(roomId).getAdminUser();
		synchronized (sessions) {
			for (IoSession sessionTmp : sessions) {
				if (sessionTmp.getAttribute("roomId").equals(roomId)) {
						usrTableAdmin += "<tr class='user'"
								+ " onclick=\"userKick(\'" + sessionTmp.getAttribute("user") + "\')\"><td>"
								+ sessionTmp.getAttribute("user") + "</td></tr>";
						usrTable += "<tr class='user'><td>"
								+ sessionTmp.getAttribute("user") + "</td></tr>";
				}
			}
		}
		usrTable += "</table>";
		usrTableAdmin += "</table>";
		synchronized (sessions) {
			for (IoSession sessionTmp : sessions) {
				if (sessionTmp.getAttribute("roomId").equals(roomId)) {
					if(sessionTmp.equals(adminSession))
						sessionTmp.write(str2Packet("GETUSER OK " + usrTableAdmin));
					else
						sessionTmp.write(str2Packet("GETUSER OK " + usrTable));
				}
			}
		}
	}
	public Room getRoomByRoomId(String roomId){
		Room room = null;
		synchronized (rooms) {
			for(Room roomTmp : rooms){
				if(roomId.equals(roomTmp.getRoomID()+"")){
					room = roomTmp;
					break;
				}
			}
		}
		return room;
	}
	public IoSession getSessionByUserName(String userName){
		IoSession session = null;
		synchronized (sessions) {
			for (IoSession sessionTmp : sessions) {
				if ( sessionTmp.isConnected() &&
						(userName.equals(sessionTmp.getAttribute("user"))) ){
					session = sessionTmp;
				}
			}
		}
		return session;
	}
	public boolean isChatUser(String name) {
		return users.contains(name);
	}

	public int getNumberOfUsers() {
		return users.size();
	}

	public void kick(String name) {
		synchronized (sessions) {
			for (IoSession session : sessions) {
				if (name.equals(session.getAttribute("user"))) {
					session.closeNow();
					break;
				}
			}
		}
	}
}
