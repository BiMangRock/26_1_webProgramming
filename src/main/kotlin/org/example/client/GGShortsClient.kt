package org.example.client
//
//import org.example.User
//import org.example.Media
//import kotlinx.coroutines.reactor.awaitSingle
//import kotlinx.coroutines.reactor.awaitSingleOrNull
//import kotlinx.coroutines.runBlocking
//import org.slf4j.LoggerFactory
//import org.springframework.core.io.buffer.DataBuffer
//import org.springframework.core.io.buffer.DataBufferUtils
//import org.springframework.http.MediaType
//import org.springframework.web.reactive.function.client.WebClient
//import java.io.File
//import java.nio.file.StandardOpenOption
//import java.util.concurrent.atomic.AtomicLong
//
//private val log = LoggerFactory.getLogger("GGShortsClient")
//
//fun main() = runBlocking {
//    val baseUrl = "http://localhost:8080" //접속해야하는 url주소입니다 <<<<<<<<<<<<<<<<<<
//    val client = WebClient.create(baseUrl)
//
//    log.info("==================================================")
//    log.info("GG-SHORTS 1st STAGE INSPECTION CLIENT START")
//    log.info("==================================================")
//
//    // --------------------------------------------------
//    // 1. DATABASE CRUD 연동 테스트
//    // --------------------------------------------------
//    log.info("\n>>> [CRUD TEST 1] Create Gamer User...")
//    val gamer = User(username = "ShowMaker_Fan", email = "dkshowmaker@gmail.com")
//    val savedUser = client.post()
//        .uri("/api/users/signup") // signup API
//        .contentType(MediaType.APPLICATION_JSON)
//        .bodyValue(gamer)
//        .retrieve()
//        .bodyToMono(User::class.java)
//        .awaitSingle()
//    log.info("  -> Saved User Information: {}", savedUser)
//
//    log.info("\n>>> [CRUD TEST 2] Get Gamer User Details...")
//    val fetchedUser = client.get()
//        .uri("/api/users/${savedUser.username}") // 사용자명 검색 API
//        .retrieve()
//        .bodyToMono(User::class.java)
//        .awaitSingleOrNull()
//    log.info("  -> Read Result: {}", fetchedUser)
//
//
//    // --------------------------------------------------
//    // [추가된 CRUD TEST 2-1] Update Gamer User (수정 테스트)
//    // --------------------------------------------------
//    log.info("\n>>> [CRUD TEST 2-1] Update Gamer User Information...")
//    val updatedInfo = User(username = "ShowMaker_Fan_Updated", email = "showmaker.new@gmail.com")
//    val updatedUser = client.put()
//        .uri("/api/users/${savedUser.id}") // PUT 수정 API 호출
//        .contentType(MediaType.APPLICATION_JSON)
//        .bodyValue(updatedInfo)
//        .retrieve()
//        .bodyToMono(User::class.java)
//        .awaitSingle()
//    log.info("  -> Updated User Information: {}", updatedUser)
//
//
//
//
//    log.info("\n>>> [CRUD TEST 3] Get Media List...")
//    val mediaList = client.get()
//        .uri("/api/media") // 전체 목록 조회 API
//        .retrieve()
//        .bodyToFlux(Media::class.java)
//        .collectList()
//        .awaitSingle()
//    log.info("  -> Registered Media Count: {}", mediaList.size)
//    mediaList.forEach { log.info("     - Media item: {}", it) }
//
//    // --------------------------------------------------
//    // 2. 비동기 동영상/오디오 스트리밍 전송 연동 테스트 (중요!)
//    // --------------------------------------------------
//    log.info("\n>>> [CRUD TEST 4] Testing Client-Side Media Streaming Download...")
//
//    // index.html 화면이나 API를 통해 미리 업로드된 미디어가 데이터베이스에 있다면,
//    // 첫 번째 미디어 데이터를 자동으로 선택하여 백엔드로부터 비동기 청크 스트리밍 다운로드를 실행합니다.
//    val firstMedia = mediaList.firstOrNull()
//    if (firstMedia != null) {
//        val targetFile = if (firstMedia.fileType == "VIDEO") File("downloaded_video.mp4") else File("downloaded_audio.mp3")
//        downloadMedia(client, "/api/media/stream/${firstMedia.id}", targetFile, firstMedia.contentType)
//    } else {
//        log.info("  -> No registered media found in DB. (For testing client-streaming, please upload a video/audio file on index.html first!)")
//    }
//
//    log.info("\n>>> [CRUD TEST 5] Delete User...")
//    val deleteResponse = client.delete()
//        .uri("/api/users/${savedUser.id}")
//        .retrieve()
//        .toBodilessEntity()
//        .awaitSingleOrNull()
//    log.info("  -> User Delete Status: {}", deleteResponse?.statusCode)
//
//    log.info("\n==================================================")
//    log.info("CLIENT CRUD TESTS COMPLETED!")
//    log.info("==================================================")
//}
//
//// Ch 13 강의 슬라이드의 WebClient 비동기 스트리밍 다운로드 이식 및 구현체
//suspend fun downloadMedia(client: WebClient, uri: String, file: File, mimeType: String) {
//    log.info("  Requesting streaming to: {}", uri)
//    val response = client.get()
//        .uri(uri)
//        .accept(MediaType.valueOf(mimeType))
//        .retrieve()
//        .toEntityFlux(DataBuffer::class.java)
//        .awaitSingle()
//
//    val totalSize = response.headers.contentLength
//    val streamData = response.body ?: return
//    log.info("    -> Response OK. Size: {} bytes", totalSize)
//
//    val currentBytes = AtomicLong(0)
//    val writeFlux = streamData.doOnNext { buffer ->
//        val received = buffer.readableByteCount()
//        val totalReceived = currentBytes.addAndGet(received.toLong())
//        if (totalSize > 0) {
//            val progress = totalReceived * 100 / totalSize
//            print("\r    [Downloading Progress] : $progress% ($totalReceived / $totalSize bytes)")
//        } else {
//            print("\r    [Downloading Progress] : $totalReceived bytes")
//        }
//    }
//
//    DataBufferUtils.write(
//        writeFlux,
//        file.toPath(),
//        StandardOpenOption.CREATE,
//        StandardOpenOption.TRUNCATE_EXISTING,
//        StandardOpenOption.WRITE
//    ).awaitSingleOrNull()
//
//    log.info("\n    -> [SUCCESS] Media file saved: {}", file.absolutePath)
//}