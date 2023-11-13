package com.project.ACG.service;

import com.project.ACG.entity.User;
import com.project.ACG.entity.UserDto;
import com.project.ACG.entity.UserUpdateRequest;
import com.project.ACG.entity.UserUpdateResponse;
import com.project.ACG.repository.UserJpaRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

	@Value("${myapp.secret-token}")
	private String secretToken;

	private final UserJpaRepository userJpaRepository;

	public ResponseEntity<byte[]> getUserListToCSV(String token) {

		// token이 없거나 token이 일치하지 않을 경우
		if (token == null || !token.equals(secretToken)) {
			return new ResponseEntity<>(null, HttpStatus.UNAUTHORIZED);
		}

		// CSV 파일의 내용을 문자열로 생성
		StringBuilder csvData = new StringBuilder();

		// CSV 헤더 행 생성
		csvData.append("ID,user_ID,user_Name,user_Email,user_Token,user_Repo,status,update_time\n");

		// 사용자 목록을 가져오고 CSV 데이터를 추가
		List<User> userList = userJpaRepository.findAll();
		for (User user : userList) {
			csvData.append(user.getId()).append(",")
				.append(user.getUserId()).append(",")
				.append(user.getUserName()).append(",")
				.append(user.getUserEmail()).append(",")
				.append(user.getUserToken()).append(",")
				.append(user.getUserRepo()).append(",")
				.append(user.isStatus()).append(",")
				.append(user.getUpdateTime()).append("\n");
		}

		// CSV 문자열을 바이트 배열로 변환
		byte[] csvBytes = csvData.toString().getBytes(StandardCharsets.UTF_8);

		// CSV 파일을 HTTP 응답으로 반환
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.parseMediaType("text/csv"));
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");
		String currentDateTime = LocalDateTime.now().format(formatter);
		headers.setContentDispositionFormData("attachment", "users_" + currentDateTime + ".csv");

		return new ResponseEntity<>(csvBytes, headers, HttpStatus.OK);
	}

	@Transactional
	public UserDto userInfo(String userId, String userEmail, String userName) {
		Optional<User> user = userJpaRepository.findUserByUserIdAndUserEmail(userId, userEmail);

		if (!userJpaRepository.existsUserByUserIdAndUserEmail(userId, userEmail)) {
			return UserDto.create(false, null, null);
		}
		User targetUser = user.get();
		targetUser.updateName(userName);
		userJpaRepository.save(targetUser);

		boolean status = false;
		if (targetUser.isStatus()) {
			status = true;
		}

		String updateTime = null;
		if (targetUser.getUpdateTime() != null) {
			updateTime = targetUser.getUpdateTime();
		}

		String repoName = null;
		if (targetUser.getUserRepo() != null) {
			repoName = targetUser.getUserRepo();
		}

		return UserDto.create(status, updateTime, repoName);
	}

	@Transactional
	public ResponseEntity<UserUpdateResponse> updateUser(UserUpdateRequest userUpdateRequest, String token) {

		// token이 없거나 token이 일치하지 않을 경우
		if (token == null || !token.equals(secretToken)) {
			return new ResponseEntity<>(UserUpdateResponse.create(0L, "error", "error", "토큰이 없거나 토큰이 정확하지 않습니다."), HttpStatus.UNAUTHORIZED);
		}

		Long id = userUpdateRequest.getId().longValue();
		String userId = userUpdateRequest.getUserId();
		String userEmail = userUpdateRequest.getUserEmail();
		String updateTime = userUpdateRequest.getUpdateTime();
		String error = userUpdateRequest.getError();

		Optional<User> user = userJpaRepository.findUserByUserIdAndUserEmail(userId, userEmail);

		if (!user.isPresent()) {
			return new ResponseEntity<>(UserUpdateResponse.create(id, userId, userEmail, "존재하지 않는 유저입니다."),HttpStatus.BAD_REQUEST);
		}

		User existUser = user.get();

		if (error == null) {
			existUser.updateAt(updateTime);
		} else {
			int startIndex = error.indexOf("error:");
			startIndex += "error:".length();

			int endIndex = error.indexOf("|");
			if (endIndex == -1) {
				endIndex = error.length();
			}

			String parsedString = error.substring(startIndex, endIndex);
			parsedString = parsedString.replace("_", " ");

			existUser.updateAt(parsedString.trim());
		}
		userJpaRepository.save(existUser);
		return new ResponseEntity<>(new UserUpdateResponse(id, userId, userEmail, "updateTime : " + existUser.getUpdateTime()), HttpStatus.OK);
	}
}

