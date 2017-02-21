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
	
	private final Set<IoSession> sessions = Collections.synchronizedSet(new HashSet<IoSession>());
	private final Set<String> users = Collections.synchronizedSet(new HashSet<String>());
	private final Set<Room> rooms = Collections.synchronizedSet(new HashSet<Room>());

	public ChatProtocolHandler() {
		// TODO Auto-generated constructor stub
		rooms.add(new Room());
	}

	@Override
	public void exceptionCaught(IoSession session, Throwable cause) {
		LOGGER.warn("Unexpected exception.", cause);
		session.closeNow();
	}

	@Override
	public void messageReceived(IoSession session, Object message) {
		Logger log = LoggerFactory.getLogger(ChatProtocolHandler.class);
		String theMessage;

		IoBuffer buffer = (IoBuffer) message;		// message -> buffer
		byte[] bt = buffer.array();					// buffer -> byte array
		theMessage = new String(bt);				// byte array -> string
		String result[] = theMessage.split(" ", 2);	// string -> command + message
		String theCommand = result[0];				//             [0]       [1]
		
		log.info("result: " + theMessage);

		try {
			ChatCommand command = ChatCommand.valueOf(theCommand);
			String user = (String) session.getAttribute("user");

			switch (command.toInt()) {

			case ChatCommand.QUIT:
				refreshUsers("0");
				session.closeNow();
				break;
			case ChatCommand.LOGIN:

				if (user != null) {
					session.write(str2Packet("LOGIN ERROR user " + user + " already logged in."));
					return;
				}

				if (result.length == 2) {
					user = result[1];
				} else {
					session.write(str2Packet("LOGIN ERROR invalid login command."));
					return;
				}
				if (users.contains(user)) {
					session.write(str2Packet("LOGIN ERROR the name " + user + " is already used."));
					return;
				}

				sessions.add(session);

				session.setAttribute("user", user);
				MdcInjectionFilter.setProperty(session, "user", user);
				users.add(user);

				session.setAttribute("roomId", "0");
				MdcInjectionFilter.setProperty(session, "roomId", "0");
				
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
			case ChatCommand.JOIN: // Written By HJ & ESP
				Room targetRoom = getRoomByRoomId(result[1]);
				int roomSize = targetRoom.getSize();
				String crTitle = targetRoom.getTitle();
				
				if (result.length == 2 && getRoomSessionCnt(targetRoom) < roomSize) {
					setRoomid(session, result[1]);
					session.write(str2Packet("JOIN OK"));
					if(targetRoom.getAdminUser().equals(session)){
						session.write(str2Packet("CREATE OK "+targetRoom.getRoomID()));
					}
					roomBroadcast("The user " + user + " has joined this Room.", result[1]);
					refreshUsers(result[1]);
					refreshUsers("0");
					refreshRoom();
				} else {
					session.write(str2Packet("JOIN FAIL " + printRooms()));
					crTitle = "LOBBY";
				}
				session.write(str2Packet("SETTITLE OK " + crTitle));
				break;
			case ChatCommand.CREATE:
				if (result.length == 2) {
					String[] roomArg = result[1].split(" ", 2);
					int rootSize = Integer.parseInt(roomArg[1]);
					String roomTitle = roomArg[0];
					
					Room room = new Room(session, rootSize, roomTitle, ++roomIdSeq);
					rooms.add(room);
					
					setRoomid(session, String.valueOf(roomIdSeq));
					session.write(str2Packet("JOIN OK"));
					session.write(str2Packet("CREATE OK "+roomIdSeq));
					session.write(str2Packet("SETTITLE OK " + roomTitle));
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
				Room room = getRoomByRoomId(id);
				
				synchronized (sessions) {
					for (IoSession sessionTmp : sessions) {
						if (sessionTmp.isConnected() && (id.equals(sessionTmp.getAttribute("roomId").toString()))) {
							setRoomid(sessionTmp, "0");
							sessionTmp.write(str2Packet("DESTROY OK"));
							sessionTmp.write(str2Packet("SETTITLE OK LOBBY"));
						}		
					}
				}
				rooms.remove(room);
				refreshRoom();
				refreshUsers("0");
				break;
			case ChatCommand.USERKICK: // Written By Ten
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
					kickUser.write(str2Packet("SETTITLE OK LOBBY"));
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
		if(!roomId.equals("0")){
			roomBroadcast("The user " + user + " has left this Room.", roomId);
			refreshUsers(roomId);
		}
		refreshUsers("0");
		refreshRoom();
	}
	
	/**
	 * 대기실(roomId == 0)에 있는 사람들에게 최신 채팅방 상태를 전달하기 위한 메서드.
	 * @param	None
	 * @return	None
	 */
	public void refreshRoom(){
		synchronized (sessions) {
			String roomTable = printRooms();
			for (IoSession sessionTmp : sessions) {
				if (sessionTmp.isConnected() && ("0".equals(sessionTmp.getAttribute("roomId").toString()))) {
					sessionTmp.write(str2Packet("LEAVE OK " + roomTable));
				}
			}
		}
	}
	/**
	 * 스트링을 Websocket Packet형태로 바꿔주는 메서드.
	 * @param 	str		Packet형태로 바꿀 문자열
	 * @return			str의 내용이 포함된 Websocket Packet
	 */
	public WebSocketCodecPacket str2Packet(String str) {
		IoBuffer buf = IoBuffer.allocate(str.length()).setAutoExpand(true);

		try {
			buf.putString(str, ENCODER);
		} catch (CharacterCodingException e) {
			e.printStackTrace();
		}
		buf.flip();
		return WebSocketCodecPacket.buildPacket(buf);
	}

	/**
	 * 특정 채팅방 안에 있는 사람들에게 메시지를 전달하는 메서드.
	 * @param message	보낼 메시지
	 * @param roomId	메시지가 뿌려질 방의 id
	 */
	public void roomBroadcast(String message, String roomId) {
		synchronized (sessions) {
			for (IoSession sessionTmp : sessions) {
				if (sessionTmp.isConnected() && (roomId.equals(sessionTmp.getAttribute("roomId").toString()))) {
					sessionTmp.write(str2Packet("BROADCAST OK " + message));
				}
			}
		}
	}
	/**
	 * 채팅방 안에 있는 사람들의 수를 세어주는 메서드.
	 * @param room	Target이 되는 채팅방의 인스턴스
	 * @return		채팅방에 참여하고 있는 session의 수
	 */
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
	/**
	 * session의 attribute에 roomId를 주입해주는 메서드.
	 * @param session	Target이 되는 세션
	 * @param roomId	주입하고자 하는 roomId
	 */
	public void setRoomid(IoSession session, String roomId) {
		session.setAttribute("roomId", roomId);
		MdcInjectionFilter.setProperty(session, "roomId", roomId);
	}
	/**
	 * 현재 채팅이 진행중인 방의 목록을 html table tag형태로 만들어주는 메서드.
	 * @return	생성된 html tag가 string 평태로 리턴
	 */
	public String printRooms() {
		String roomTable = "<table id='roomList'>";
		synchronized (rooms) {
			for (Room roomTmp : rooms) {
				if (roomTmp.getRoomID() != 0) {
					roomTable += "<tr id='room" + roomTmp.getRoomID() + "' onclick='roomJoin(" + roomTmp.getRoomID()
							+ ")'>" + "<td class='roomTitle'>" + roomTmp.getTitle() + "</td>" + "<td class='roomSize'>"
							+ getRoomSessionCnt(roomTmp) + "/" + roomTmp.getSize() + "</td>" + "</tr>";
				}
			}
		}
		roomTable += "</table>";
		return roomTable;
	}
	/**
	 * Target이 되는 채팅방에 참여하고 있는 사람들에게 그 방의 참여자 정보를 html tag table형태로 전달하는 메서드.
	 * @param roomId	Target이 되는 채팅방의 id
	 */
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
	/**
	 * roomId로 Room 인스턴스를 찾아 리턴해 주는 메서드.
	 * @param roomId	찾고자하는 방의 id
	 * @return			Room 인스턴스
	 */
	public Room getRoomByRoomId(String roomId){
		synchronized (rooms) {
			for(Room roomTmp : rooms){
				if(roomId.equals(roomTmp.getRoomID()+"")){
					return roomTmp;
				}
			}
		}
		return null;
	}
	/**
	 * username으로 Session을 찾아 리턴해 주는 메서드.
	 * @param userName	찾고자 하는 Session의 username
	 * @return			session
	 */
	public IoSession getSessionByUserName(String userName){
		synchronized (sessions) {
			for (IoSession sessionTmp : sessions) {
				if ( sessionTmp.isConnected() &&
						(userName.equals(sessionTmp.getAttribute("user"))) ){
					return sessionTmp;
				}
			}
		}
		return null;
	}
}
