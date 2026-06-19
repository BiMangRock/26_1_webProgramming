//22411895 김창민
package org.example.controller

import org.example.domain.Media
import org.example.domain.User
import org.example.service.MediaService
import org.example.service.SaveMediaResult
import org.example.service.UpdateMediaResult
import org.example.service.UserService
import org.slf4j.LoggerFactory
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.codec.multipart.FilePart
import org.springframework.web.bind.annotation.*
import java.io.File

/**
 * [전체 RESTful HTTP 게이트웨이 엔드포인트 조율]
 * 웹 클라이언트 브라우저와 통신하며 데이터 정렬 포맷을 조율함.
 */
@RestController
@CrossOrigin("*")
class GGShortsController(
    private val userService: UserService,
    private val mediaService: MediaService
) {
    /** 서버 트래픽 감지 및 런타임 진행 모니터링을 관측하기 위한 SLF4J 디버깅 라이브러리임 */
    private val log = LoggerFactory.getLogger(GGShortsController::class.java)

    /**
     * [가입 양식 신규 영속 요청 수용]
     */
    @PostMapping("/api/users/signup")
    suspend fun signUp(@RequestBody user: User): ResponseEntity<User> {
        log.info("Sign up request: username=${user.username}, email=${user.email}")
        val registered = userService.register(user)
        return ResponseEntity.status(HttpStatus.CREATED).body(registered)
    }

    /**
     * [회원 유효 유무 단건 검진]
     */
    @GetMapping("/api/users/{username}")
    suspend fun findUser(@PathVariable username: String): ResponseEntity<User> {
        log.info("Search user request: username=$username")
        val user = userService.getByUsername(username)
        return if (user != null) ResponseEntity.ok(user) else ResponseEntity.notFound().build()
    }

    /**
     * [대용량 멀티파트 형태 비디오 업로드 가드 라우터]
     * Multipart/form-data 헤더 요청으로부터 수집하여, 미디어 서비스를 통해 중복 회피를 엄밀화함.
     */
    @PostMapping("/api/media/upload", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    suspend fun uploadMedia(
        @RequestPart("title") title: String,
        @RequestPart("type") type: String,
        @RequestPart("file") filePart: FilePart,
        @RequestPart("uploader") uploader: String
    ): ResponseEntity<Any> {
        log.info("Media Upload request: title=$title, type=$type, filename=${filePart.filename()}, uploader=$uploader")
        return when (val result = mediaService.saveMedia(title, type, filePart, uploader)) {
            is SaveMediaResult.Success -> ResponseEntity.status(HttpStatus.CREATED).body(result.media)
            is SaveMediaResult.DuplicateTitle -> ResponseEntity.status(HttpStatus.CONFLICT)
                .body(mapOf("message" to "이미 동일한 제목의 동영상이 존재합니다. 다른 이름을 사용해 주세요."))
        }
    }

    /**
     * [업로드된 자원 컬렉션 목록 갱신 획득]
     */
    @GetMapping("/api/media")
    suspend fun getMediaList(): List<Media> {
        log.info("Fetch media list request")
        return mediaService.getAllMedia()
    }

    /**
     * [미디어 파일 타이틀 갱신 권한 인가 라우팅]
     */
    @PutMapping("/api/media/{id}")
    suspend fun updateMedia(
        @PathVariable id: Long,
        @RequestParam("title") title: String,
        @RequestParam("username") username: String
    ): ResponseEntity<Void> {
        log.info("Update media title request: id=$id, newTitle=$title, requestedBy=$username")
        return when (mediaService.updateMedia(id, title, username)) {
            UpdateMediaResult.SUCCESS -> ResponseEntity.ok().build()
            UpdateMediaResult.FORBIDDEN -> ResponseEntity.status(HttpStatus.FORBIDDEN).build()
            UpdateMediaResult.NOT_FOUND -> ResponseEntity.notFound().build()
        }
    }

    /**
     * [미디어 물리 파일 완전 처분 라우터]
     */
    @DeleteMapping("/api/media/{id}")
    suspend fun deleteMedia(
        @PathVariable id: Long,
        @RequestParam("username") username: String
    ): ResponseEntity<Void> {
        log.info("Delete media request: id=$id, requestedBy=$username")
        return when (mediaService.deleteMedia(id, username)) {
            MediaService.DeleteResult.SUCCESS -> ResponseEntity.noContent().build()
            MediaService.DeleteResult.FORBIDDEN -> ResponseEntity.status(HttpStatus.FORBIDDEN).build()
            MediaService.DeleteResult.NOT_FOUND -> ResponseEntity.notFound().build()
        }
    }

    /**
     * [미디어 바이너리 다이렉트 스트리밍 공급 통로]
     * Accept-Ranges 헤더 전송을 적용하여 브라우저 타겟에 대용량 비디오 정렬 파셜 응답(Seeking)을 유연하게 구현함.
     */
    @GetMapping("/api/media/stream/{id}")
    suspend fun streamMedia(@PathVariable id: Long): ResponseEntity<Resource> {
        val media = mediaService.getMediaById(id) ?: return ResponseEntity.notFound().build()
        val file = File("uploads", media.fileName)
        if (!file.exists()) {
            log.warn("Physical file not found for media ID: $id")
            return ResponseEntity.notFound().build()
        }

        log.info("Streaming Media: title=${media.title}, type=${media.fileType}, file=${file.name}")
        val resource = FileSystemResource(file)

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(media.contentType))
            .header("Accept-Ranges", "bytes") // 미디어 청크 분할 전송 승인 용도 헤더
            .body(resource)
    }

    /**
     * [임의 회원 레코드 세부 갱신 라우터]
     */
    @PutMapping("/api/users/{id}")
    suspend fun updateUser(
        @PathVariable id: Long,
        @RequestBody user: User
    ): ResponseEntity<User> {
        log.info("Update user request: id=$id, newUsername=${user.username}, newEmail=${user.email}")
        val updated = userService.updateUser(id, user)
        return if (updated != null) {
            ResponseEntity.ok(updated)
        } else {
            ResponseEntity.notFound().build()
        }
    }

    /**
     * [특정 대상 회원 인가 권한 기준 파괴 탈퇴 라우터]
     */
    @DeleteMapping("/api/users/{id}")
    suspend fun deleteUser(
        @PathVariable id: Long,
        @RequestParam("username") username: String
    ): ResponseEntity<Void> {
        log.info("Delete user request: targetId=$id, requestedBy=$username")
        return when (userService.deleteUser(id, username)) {
            UserService.DeleteUserResult.SUCCESS -> ResponseEntity.noContent().build()
            UserService.DeleteUserResult.FORBIDDEN -> ResponseEntity.status(HttpStatus.FORBIDDEN).build()
            UserService.DeleteUserResult.NOT_FOUND -> ResponseEntity.notFound().build()
        }
    }
}