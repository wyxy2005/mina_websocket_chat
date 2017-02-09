<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<!-- Written By Ten -->
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<script src="http://code.jquery.com/jquery-2.1.3.js"></script>
<title>Room Create</title>
<script type="text/javascript">
	function roomEvent() {

		var size = $('#size').val();
		var title = $('#title').val();
		if(size != "" && title !=""){
			opener.parent.createRoom(size, title);
			self.close();
		}else
			alert("Room size and title must be required.");
	}
</script>
</head>
<style>
body {
	margin: 0;
}

#roomInfo {
	float: left;
	border: 10px solid #dfdfdf;
	height: 180px;
	width: 480px;
	margin: 0;
}

.col1 {
	float: left;
	border: 10px solid #dfdfdf;
	width: 120px;
	height: 50px;
	text-align: center;
	vertical-align: middle;
	line-height: 50px;
}

.col2 {
	padding: 0;
	float: left;
	border: 10px solid #dfdfdf;
	width: 320px;
	height: 50px;
	text-align: center;
}

#btn {
	float: left;
	width: 480px;
	padding: 0;
}

button {
	float: left;
	width: 100%;
	height:40px;
	border: 0;
}
</style>
<body>
	<div id="roomInfo">
		<div class="col1">제한 인원</div>
		<input class="col2" type="number" id="size" />
		<div class="col1">채팅방 이름</div>
		<input class="col2" type="text" id="title" />
		<button id="createroom" onclick="roomEvent()">확인</button>
	</div>
</body>
</html>