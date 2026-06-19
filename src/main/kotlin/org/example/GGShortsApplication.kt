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
    val password: String
)

@Table("media")
data class Media(
    @Id val id: Long? = null,
    val title: String,
    val fileType: String,
    val fileName: String,
    val contentType: String,
    val uploadedBy: String
)

// ==========================================
// 2. REPOSITORY (Reactive R2DBC)
// ==========================================
interface UserRepository : CoroutineCrudRepository<User, Long> {
    suspend fun findByUsername(username: String): User?
}

interface MediaRepository : CoroutineCrudRepository<Media, Long> {
    // 제목 중복 여부 확인을 위한 쿼리 메소드 추가
    suspend fun findByTitle(title: String): Media?
}

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
            password = updatedUser.password
        )
        return userRepository.save(userToSave)
    }



    // UserService 클래스 내부의 deleteUser 함수를 아래 내용으로 교체합니다.
    suspend fun deleteUser(id: Long, requestor: String): DeleteUserResult {
        // 1. 삭제 대상 유저가 존재하는지 확인
        val user = userRepository.findById(id) ?: return DeleteUserResult.NOT_FOUND

        // 2. 요청자가 admin이 아니고, 삭제 대상의 username과도 다르다면 권한 거부(Forbidden)
        if (requestor != "admin" && user.username != requestor) {
            return DeleteUserResult.FORBIDDEN
        }

        userRepository.deleteById(id)
        return DeleteUserResult.SUCCESS
    }

    // 결과 분기를 명확히 처리하기 위한 Enum 클래스 정의
    enum class DeleteUserResult {
        SUCCESS, FORBIDDEN, NOT_FOUND
    }

}

// 업로드 처리 결과 표현용 클래스 정의
sealed interface SaveMediaResult {
    data class Success(val media: Media) : SaveMediaResult
    object DuplicateTitle : SaveMediaResult
}

// 제목 수정 처리 결과 표현용 Enum
enum class UpdateMediaResult {
    SUCCESS, FORBIDDEN, NOT_FOUND
}

@Service
class MediaService(private val mediaRepository: MediaRepository) {
    private val uploadDir = File("uploads").apply { if (!exists()) mkdirs() }

    suspend fun saveMedia(title: String, fileType: String, filePart: FilePart, uploadedBy: String): SaveMediaResult {
        // [중복 검증] 동일한 제목의 미디어가 DB에 존재하는지 확인
        val existing = mediaRepository.findByTitle(title)
        if (existing != null) {
            return SaveMediaResult.DuplicateTitle
        }

        // [덮어쓰기 방지] 물리 파일 저장 시 고유 타임스탬프를 조합하여 파일명 중복 충돌을 방지합니다.
        val uniqueFileName = "${System.currentTimeMillis()}_${filePart.filename()}"
        val destination = File(uploadDir, uniqueFileName)
        filePart.transferTo(destination).awaitSingleOrNull()

        val media = Media(
            title = title,
            fileType = fileType,
            fileName = uniqueFileName,
            contentType = filePart.headers().contentType?.toString() ?: "application/octet-stream",
            uploadedBy = uploadedBy
        )
        val saved = mediaRepository.save(media)
        return SaveMediaResult.Success(saved)
    }

    suspend fun getAllMedia(): List<Media> = mediaRepository.findAll().toList()

    suspend fun getMediaById(id: Long): Media? = mediaRepository.findById(id)

    // [미디어 제목 수정 비즈니스 로직] 본인이 올렸거나 admin 권한을 가졌는지 검증 후 제목 수정
    suspend fun updateMedia(id: Long, newTitle: String, requestor: String): UpdateMediaResult {
        val media = mediaRepository.findById(id) ?: return UpdateMediaResult.NOT_FOUND

        if (requestor != "admin" && media.uploadedBy != requestor) {
            return UpdateMediaResult.FORBIDDEN
        }

        val updatedMedia = media.copy(title = newTitle)
        mediaRepository.save(updatedMedia)
        return UpdateMediaResult.SUCCESS
    }

    suspend fun deleteMedia(id: Long, requestor: String): DeleteResult {
        val media = mediaRepository.findById(id) ?: return DeleteResult.NOT_FOUND

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

    @PostMapping("/api/users/signup")
    suspend fun signUp(@RequestBody user: User): ResponseEntity<User> {
        log.info("Sign up request: username=${user.username}, email=${user.email}")
        val registered = userService.register(user)
        return ResponseEntity.status(HttpStatus.CREATED).body(registered)
    }

    @GetMapping("/api/users/{username}")
    suspend fun findUser(@PathVariable username: String): ResponseEntity<User> {
        log.info("Search user request: username=$username")
        val user = userService.getByUsername(username)
        return if (user != null) ResponseEntity.ok(user) else ResponseEntity.notFound().build()
    }

    // [Media - Create] 업로드 컨트롤러 (중복 검사 분기 적용)
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

    @GetMapping("/api/media")
    suspend fun getMediaList(): List<Media> {
        log.info("Fetch media list request")
        return mediaService.getAllMedia()
    }

    // [Media - Update] 동영상 제목 수정 API 추가
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

    // GGShortsController 클래스 내부의 deleteUser 함수를 아래 내용으로 교체합니다.
    @DeleteMapping("/api/users/{id}")
    suspend fun deleteUser(
        @PathVariable id: Long,
        @RequestParam("username") username: String // 삭제를 요청한 사람의 아이디
    ): ResponseEntity<Void> {
        log.info("Delete user request: targetId=$id, requestedBy=$username")
        return when (userService.deleteUser(id, username)) {
            UserService.DeleteUserResult.SUCCESS -> ResponseEntity.noContent().build()
            UserService.DeleteUserResult.FORBIDDEN -> ResponseEntity.status(HttpStatus.FORBIDDEN).build()
            UserService.DeleteUserResult.NOT_FOUND -> ResponseEntity.notFound().build()
        }
    }

}