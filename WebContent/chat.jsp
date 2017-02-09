<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">

<html>
<head>
<%
	String address = request.getParameter("address");
	String username = request.getParameter("username");
%>
<script type="text/javascript"
	src="http://code.jquery.com/jquery-2.1.3.js"></script>
<link rel="stylesheet" type="text/css" href="css/main.css">
<link rel="shortcut icon" href="css/pi.ico"/>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<title>Mina-Websocket Chat Client</title>
<script type="text/javascript">
	var ws = new WebSocket("ws://<%=address%>");
	ws.onopen = function() {
		ws.send("LOGIN <%=username%>");
	};
	window.onbeforeunload = function() {
		ws.send("QUIT");
		ws.close();
	};
	ws.onmessage = function(event) {
		var reader;
		if (event.data instanceof Blob) {
			reader = new FileReader();
			reader.onload = function() {
				var message = reader.result;
				var result = message.split(" ");
				var status = result[1];
				var theCommand = result[0];
				if (status == "OK") {
					switch (theCommand) {
					case "BROADCAST":
						var i, len, message;
						len = result.length;
						message = "";
						for (i = 2; i < len; i++) {
							message += result[i] + " ";
						}
						$("#chatArea").append(message + "<br>");
						break;
					case "LOGIN":
						var i, len, message;
						len = result.length;
						message = "";
						for (i = 2; i < len; i++) {
							message += result[i] + " ";
						}
						$("#chatArea").append(message + "<br>");
						break;
					case "JOIN":
						$("#chatArea").empty();
						$("#chatInputBox").attr("disabled", false);
						$("#leaveBtn").show();
						$("#createBtn").hide();
						var i, len, message;
						len = result.length;
						message = "";
						for (i = 2; i < len; i++) {
							message += result[i] + " ";
						}
						$("#chatArea").append(message);
						break;
					case "LEAVE":
						$("#userList2").empty();
						$("#chatArea").empty();
						$("#chatInputBox").attr("disabled", true);
						$("#destroyBtn").hide();
						$("#leaveBtn").hide();
						$("#createBtn").show();
						var i, len, message;
						len = result.length;
						message = "";
						for (i = 2; i < len; i++) {
							message += result[i] + " ";
						}
						$("#chatArea").append(message + "<br>");
						break;
					case "CREATE":
						$("#destroyBtn").show();
						$("#destroyBtn").onclick = function(){ roomDestroy(result[2]); };
						break;
					case "GETUSER":
						$("#userList2").empty();
						var i, len, message;
						len = result.length;
						message = "";
						for(i=2; i<len; i++){
							message += result[i]+" ";
						}
						$('#userList2').append(message);
						break;
					}
				} else if (status == "FAIL") {
					switch (theCommand) {
					case "JOIN":
						alert("Room Join Failed, Room does not exist or full.");
						$("#chatArea").empty();
						$("#chatInputBox").attr("disabled", true);
						$("#leaveBtn").hide();
						$("#createBtn").show();
						var i, len, message;
						len = result.length;
						message = "";
						for (i = 2; i < len; i++) {
							message += result[i] + " ";
						}
						$("#chatArea").append(message + "<br>");
						break;
					}
				}
				return console.log(reader.result);
			};
			reader.readAsText(event.data);
		}
	};
	ws.onclose = function() {
		alert("Connection is closed...");
		window.location.replace('login.html');
	};

	function roomJoin(no) {
		ws.send("JOIN " + no);
	}
	function roomLeave() {
		ws.send("LEAVE");
	}
	function chatEvent(e) {
		if (e.keyCode == 13) {
			var chatVal = $("#chatInputBox").val();
			ws.send("BROADCAST " + chatVal);
			$("#chatInputBox").val("");
			return false;
		} else {
			return true;
		}
	}
	function createRoom(size, title) {
		ws.send("CREATE " + title + " " + size);
	}

	function createPop() {/* Written By Ten */
		window.open('createRoom.jsp',
					'',
					'left=200, top=200, width=500, height=260, scrollbars=n0, status=no, resizeable=no, fullscreen=no, channelmode=no');
	}
	function roomDestroy(no){
		ws.send("DESTROY "+no);
	}
</script>
</head>
<body>
	<div id="chatWrapper">
		<div id="chatArea"></div>
		<div id="buttonList">
			<!-- <button class="Buttons" >Join</button> -->
			<button id="createBtn" class="Buttons" onclick="createPop()">Create</button>
			<button id="leaveBtn" class="Buttons" style="display: none;"
				onclick="roomLeave()">Leave</button>
			<button id="destroyBtn" class="Buttons" style="display: none;" onclick="roomDestroy()">Destroy</button>
		</div>
		<div id="userList" align="center">
				:::접속자 목록:::<br>
				<div id="userList2"></div>
		</div>
		<form id="chatInput">
			<input id="chatInputBox" type="text"
				onkeypress="return chatEvent(event)" disabled></input>
			<!-- <input type="text" style="display: none;" />
			<button id="chatInputBtn" style="display:none;" onclick="chatEvent();"></button> -->
		</form>
	</div>
</body>
</html>