package org.example

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.codec.multipart.FilePart
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.*
import java.io.File

@SpringBootApplication
class GGShortsApplication

fun main(args: Array<String>) {
    runApplication<GGShortsApplication>(*args)
}

// ==========================================
// 1. DATA MODEL (Entities)
// ==========================================
@Table("users")
data class User(
    @Id val id: Long? = null,
    val username: String,
    val email: String,
    val password: String // 비밀번호 필드 추가
)

@Table("media")
data class Media(
    @Id val id: Long? = null,
    val title: String,
    val fileType: String, // "VIDEO" or "AUDIO"
    val fileName: String,
    val contentType: String,
    val uploadedBy: String // 업로더의 username 기록
)

// ==========================================
// 2. REPOSITORY (Reactive R2DBC)
// ==========================================
interface UserRepository : CoroutineCrudRepository<User, Long> {
    suspend fun findByUsername(username: String): User?
}

interface MediaRepository : CoroutineCrudRepository<Media, Long>

// ==========================================
// 3. SERVICE LAYER (Business Logic)
// ==========================================
@Service
class UserService(private val userRepository: UserRepository) {
    suspend fun register(user: User): User = userRepository.save(user)
    suspend fun getByUsername(username: String): User? = userRepository.findByUsername(username)

    suspend fun updateUser(id: Long, updatedUser: User): User? {
        val existingUser = userRepository.findById(id) ?: return null
        val userToSave = existingUser.copy(
            username = updatedUser.username,
            email = updatedUser.email,
            password = updatedUser.password // 비밀번호 수정도 반영
        )
        return userRepository.save(userToSave)
    }
}

@Service
class MediaService(private val mediaRepository: MediaRepository) {
    private val uploadDir = File("uploads").apply { if (!exists()) mkdirs() }

    suspend fun saveMedia(title: String, fileType: String, filePart: FilePart, uploadedBy: String): Media {
        val destination = File(uploadDir, filePart.filename())
        filePart.transferTo(destination).awaitSingleOrNull()

        val media = Media(
            title = title,
            fileType = fileType,
            fileName = filePart.filename(),
            contentType = filePart.headers().contentType?.toString() ?: "application/octet-stream",
            uploadedBy = uploadedBy // 업로더 정보 저장
        )
        return mediaRepository.save(media)
    }

    suspend fun getAllMedia(): List<Media> = mediaRepository.findAll().toList()

    suspend fun getMediaById(id: Long): Media? = mediaRepository.findById(id)

    // 삭제 처리 및 결과 Enum 정의
    suspend fun deleteMedia(id: Long, requestor: String): DeleteResult {
        val media = mediaRepository.findById(id) ?: return DeleteResult.NOT_FOUND

        // 관리자 계정이거나 본인이 직접 올린 파일만 삭제할 수 있도록 제한
        if (requestor != "admin" && media.uploadedBy != requestor) {
            return DeleteResult.FORBIDDEN
        }

        val file = File(uploadDir, media.fileName)
        if (file.exists()) file.delete()
        mediaRepository.deleteById(id)
        return DeleteResult.SUCCESS
    }

    enum class DeleteResult {
        SUCCESS, FORBIDDEN, NOT_FOUND
    }
}

// ==========================================
// 4. REST CONTROLLERS
// ==========================================
@RestController
@CrossOrigin("*")
class GGShortsController(
    private val userService: UserService,
    private val mediaService: MediaService
) {
    private val log = LoggerFactory.getLogger(GGShortsController::class.java)

    // [User - Create] 회원 가입
    @PostMapping("/api/users/signup")
    suspend fun signUp(@RequestBody user: User): ResponseEntity<User> {
        log.info("Sign up request: username=${user.username}, email=${user.email}")
        val registered = userService.register(user)
        return ResponseEntity.status(HttpStatus.CREATED).body(registered)
    }

    // [User - Read] 회원 검색
    @GetMapping("/api/users/{username}")
    suspend fun findUser(@PathVariable username: String): ResponseEntity<User> {
        log.info("Search user request: username=$username")
        val user = userService.getByUsername(username)
        // 비밀번호 확인 힌트 기능 구현을 위해 User 객체를 그대로 내려줍니다.
        return if (user != null) ResponseEntity.ok(user) else ResponseEntity.notFound().build()
    }

    // [Media - Create] 미디어 파일 업로드 (uploader 추가 수신)
    @PostMapping("/api/media/upload", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    suspend fun uploadMedia(
        @RequestPart("title") title: String,
        @RequestPart("type") type: String,
        @RequestPart("file") filePart: FilePart,
        @RequestPart("uploader") uploader: String // 업로드한 주체 수신
    ): ResponseEntity<Media> {
        log.info("Media Upload request: title=$title, type=$type, filename=${filePart.filename()}, uploader=$uploader")
        val savedMedia = mediaService.saveMedia(title, type, filePart, uploader)
        return ResponseEntity.status(HttpStatus.CREATED).body(savedMedia)
    }

    // [Media - Read] 전체 목록 조회
    @GetMapping("/api/media")
    suspend fun getMediaList(): List<Media> {
        log.info("Fetch media list request")
        return mediaService.getAllMedia()
    }

    // [Media - Delete] 미디어 삭제 (요청자 아이디 requestor 검증)
    @DeleteMapping("/api/media/{id}")
    suspend fun deleteMedia(
        @PathVariable id: Long,
        @RequestParam("username") username: String // 삭제를 요청한 주체의 username 파라미터 수신
    ): ResponseEntity<Void> {
        log.info("Delete media request: id=$id, requestedBy=$username")
        return when (mediaService.deleteMedia(id, username)) {
            MediaService.DeleteResult.SUCCESS -> ResponseEntity.noContent().build()
            MediaService.DeleteResult.FORBIDDEN -> ResponseEntity.status(HttpStatus.FORBIDDEN).build()
            MediaService.DeleteResult.NOT_FOUND -> ResponseEntity.notFound().build()
        }
    }

    // [Media - Stream] 비동기 스트리밍
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
            .header("Accept-Ranges", "bytes")
            .body(resource)
    }

    // [User - Update] 회원 정보 수정
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
}