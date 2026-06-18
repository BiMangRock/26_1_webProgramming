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
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.io.File

@SpringBootApplication
class GGShortsApplication

//http://localhost:8080  에 접속

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
    val email: String
)

@Table("media")
data class Media(
    @Id val id: Long? = null,
    val title: String,
    val fileType: String, // "VIDEO" or "AUDIO"
    val fileName: String,
    val contentType: String
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
}

@Service
class MediaService(private val mediaRepository: MediaRepository) {
    private val uploadDir = File("uploads").apply { if (!exists()) mkdirs() }

    suspend fun saveMedia(title: String, fileType: String, filePart: FilePart): Media {
        val destination = File(uploadDir, filePart.filename())
        // 비동기 방식으로 파일을 서버 디렉토리에 물리 저장
        filePart.transferTo(destination).awaitSingleOrNull()

        val media = Media(
            title = title,
            fileType = fileType,
            fileName = filePart.filename(),
            contentType = filePart.headers().contentType?.toString() ?: "application/octet-stream"
        )
        return mediaRepository.save(media)
    }

    suspend fun getAllMedia(): List<Media> = mediaRepository.findAll().toList()

    suspend fun getMediaById(id: Long): Media? = mediaRepository.findById(id)

    suspend fun deleteMedia(id: Long): Boolean {
        val media = mediaRepository.findById(id) ?: return false
        val file = File(uploadDir, media.fileName)
        if (file.exists()) file.delete() // 실제 로컬 파일 삭제
        mediaRepository.deleteById(id) // DB 메타데이터 삭제
        return true
    }
}

// ==========================================
// 4. REST CONTROLLERS (HTML 100% 매핑 API)
// ==========================================
@RestController
@CrossOrigin("*") // HTML 파일 로컬 직접 실행 시 CORS 차단 방지용 안전 장치
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
        return if (user != null) ResponseEntity.ok(user) else ResponseEntity.notFound().build()
    }

    // [Media - Create] 미디어 파일 업로드 (MultipartForm 수신)
    @PostMapping("/api/media/upload", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    suspend fun uploadMedia(
        @RequestPart("title") title: String,
        @RequestPart("type") type: String,
        @RequestPart("file") filePart: FilePart
    ): ResponseEntity<Media> {
        log.info("Media Upload request: title=$title, type=$type, filename=${filePart.filename()}")
        val savedMedia = mediaService.saveMedia(title, type, filePart)
        return ResponseEntity.status(HttpStatus.CREATED).body(savedMedia)
    }

    // [Media - Read] 전체 목록 조회
    @GetMapping("/api/media")
    suspend fun getMediaList(): List<Media> {
        log.info("Fetch media list request")
        return mediaService.getAllMedia()
    }

    // [Media - Delete] 미디어 삭제
    @DeleteMapping("/api/media/{id}")
    suspend fun deleteMedia(@PathVariable id: Long): ResponseEntity<Void> {
        log.info("Delete media request: id=$id")
        val deleted = mediaService.deleteMedia(id)
        return if (deleted) ResponseEntity.noContent().build() else ResponseEntity.notFound().build()
    }

    // [Media - Stream] 비동기 동영상/오디오 청크 및 seek 스트리밍
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

        // Spring WebFlux는 Resource 반환 시 브라우저 플레이어의 Byte-Range(HTTP 206) 요청을 자체적으로 처리해 줍니다.
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(media.contentType))
            .header("Accept-Ranges", "bytes")
            .body(resource)
    }
}