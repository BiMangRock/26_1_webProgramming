// 22411895 김창민 - Concurrent Loop Client 구현부
package org.example.client

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.runBlocking
import org.example.domain.Media
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToFlux
import org.springframework.web.reactive.function.client.bodyToMono
import java.awt.Desktop
import java.io.File
import java.nio.file.StandardOpenOption
import java.util.concurrent.atomic.AtomicLong

/**
 * [WebClient 기반 비차단(Non-blocking) 비동기 멀티미디어 스트리밍 전용 클라이언트 애플리케이션임]
 * 코루틴(Coroutine)의 비동기 동시성 처리를 활용하여 다중 파일 다운로드 및 재생기 구동을 백그라운드 스레드에서 실시간으로 실행하는 멀티태스킹 아키텍처 구조임.
 */
fun main() = runBlocking {
    /** [baseUrl] 서버 게이트웨이 접속용 기본 호스트 및 포트 주소임 */
    val baseUrl = "http://localhost:8080"

    /** [client] Spring WebFlux에서 제공하는 비차단(Non-blocking) HTTP 요청 전용 WebClient 인스턴스임 */
    val client = WebClient.create(baseUrl)

    println("======================================================")
    println("🎬 GGShorts 백그라운드 스트리밍 클라이언트를 시작합니다.")
//    println("💡 여러 개의 미디어를 연속 입력하여 멀티 재생창을 동시에 띄울 수 있습니다.")
    println("💡 언제든 'q' 또는 'Q'를 입력하면 프로그램이 종료됩니다.")
    println("======================================================")

    /** [무한 대화형 루프(Interactive Loop) 알고리즘]
     * 사용자가 강제 종료('q') 명령을 내릴 때까지 무한정 반복하며 실시간 미디어 조회 및 다운로드 요청을 반복 수행하는 무한 루프 제어 구조임.
     */
    while (true) {
        println("\n🔍 서버에서 실시간 미디어 목록 조회를 요청하는 중...")

        /** [mediaList] 서버 DB로부터 실시간 가공 수집된 모든 미디어 메타데이터 엔티티 목록을 비동기 싱글 모노 타입으로 획득하여 변환한 List 컬렉션 자료구조임 */
        val mediaList = try {
            client.get()
                .uri("/api/media")
                .retrieve()
                .bodyToMono<List<Media>>()
                .awaitSingle()
        } catch (e: Exception) {
            println("❌ 서버 연결 실패: ${e.message}")
            println("🔄 5초 후 다시 조회를 시도합니다... (종료하려면 'q' 입력)")
            val input = readlnOrNull()?.trim()
            if (input?.equals("q", ignoreCase = true) == true) {
                break
            }
            delay(5000) // 코루틴을 양보 상태로 전환하는 비차단(Non-blocking) 지연 알고리즘임
            continue
        }

        if (mediaList.isEmpty()) {
            println("📭 현재 서버에 등록된 미디어가 존재하지 않습니다.")
            println("💡 웹 페이지에서 미디어를 업로드한 후 엔터를 눌러 갱신하세요. (종료: 'q')")
            val input = readlnOrNull()?.trim()
            if (input?.equals("q", ignoreCase = true) == true) {
                break
            }
            continue
        }

        println("\n============= [ 🎬 GGShorts 실시간 미디어 목록 ] =============")
        mediaList.forEach { media ->
            println("[ID: ${media.id}] 제목: ${media.title} | 타입: ${media.fileType} | 게시자: ${media.uploadedBy}")
        }
        println("============================================================")

        print("\n📥 스트리밍할 미디어 ID를 입력하세요 (종료: q): ")
        /** [userInput] 사용자의 실시간 키보드 표준 입력 스트링 값을 확보 및 보존하기 위한 입력 버퍼 변수임 */
        val userInput = readlnOrNull()?.trim() ?: ""

        // 종료 명령 감지 및 루프 파괴 처리
        if (userInput.equals("q", ignoreCase = true)) {
            println("👋 프로그램을 안전하게 종료합니다.")
            break
        }

        /** [targetIdInput] 사용자 입력 문자열을 64비트 부호 있는 정수형(Long)으로 파싱하여 DB 레코드 매핑용 식별값으로 정밀 변환한 수치형 변수임 */
        val targetIdInput = userInput.toLongOrNull()
        if (targetIdInput == null) {
            println("⚠️ 올바른 숫자 ID 또는 'q'를 입력해 주세요.")
            continue
        }

        val selectedMedia = mediaList.find { it.id == targetIdInput }
        if (selectedMedia == null) {
            println("❌ [ID: $targetIdInput]번에 해당하는 미디어가 존재하지 않습니다. 다시 확인해 주세요.")
            continue
        }

        /** [비차단 가상 스레드(Coroutine) 분기 연산 알고리즘]
         * launch(Dispatchers.IO) 스레드 할당을 통해 대용량 네트워크/디스크 파일 입출력 작업을 메인 제어 루프와 완전히 격리된 별도의 IO 스레드 풀 환경으로 던져버림.
         * 메인 스레드는 즉시 해방되어 대기 상태 없이 즉각 다음 미디어 ID 입력 요청 단계로 넘어가게 하며, 이로 인해 여러 개의 다운로드 및 플레이어 가동을 독립적으로 동시 수행(Concurrency)할 수 있게 함.
         */
        launch(Dispatchers.IO) {
            downloadAndPlayMedia(client, selectedMedia)
        }

        println("⚡ [ID: ${selectedMedia.id}] '${selectedMedia.title}' 스트리밍 다운로드 요청을 백그라운드 작업에 등록했습니다.")
        delay(300) // 비차단 컨텍스트 교환 시간차를 유도하여 백그라운드 로그와 메인 프롬프트 로그가 뒤엉키는 현상을 방지하는 정밀 조율 딜레이임
    }
}

/**
 * [downloadAndPlayMedia]
 * 비동기 논블로킹 방식으로 네트워크 미디어 바이너리 소켓 스트림 데이터를 긁어와 로컬 영속 스토리지에 세이브하고, 완수 즉시 OS 네이티브 동영상 재생 쉘을 독립 프로세스로 가동하는 핵심 코루틴 일시중단(suspend) 함수임.
 */
suspend fun downloadAndPlayMedia(client: WebClient, selectedMedia: Media) {
    val mediaId = selectedMedia.id
    val title = selectedMedia.title
    val contentType = selectedMedia.contentType

    /** [동적 파일 네이밍 회피 알고리즘 변수 (outputFile)]
     * 병렬 실행 중 동일한 미디어 ID가 동시 다운로드되거나 이전 세이브 파일이 덮어써지는 문제를 방지하기 위해 파일명 뒤에 시스템의 에포크 밀리초(Epoch Milliseconds) 타임스탬프를 난수처럼 꼬리표로 합성하는 파일 시스템 위치 고유 식별 명명용 변수임.
     */
    val fileExtension = if (contentType.contains("mp4")) "mp4" else "mp3"
    val outputFile = File("downloaded_media_${mediaId}_${System.currentTimeMillis()}.$fileExtension")

    println("\n📥 [ID: $mediaId] 백그라운드 다운로드 개시 -> 파일명: ${outputFile.name}")

    /** [totalSize]
     * 대용량 동영상 데이터 전송 전에 전송 대기 중인 본문(Body) 전체 바이트 용량을 미리 감지하기 위해 HTTP HEAD 요청을 비동기 수행하여, 메모리 낭비 없이 응답 헤더의 'Content-Length' 정수값만 초고속으로 발췌한 64비트 정수형 크기 보존 변수임.
     */
    val totalSize = try {
        client.head()
            .uri("/api/media/stream/$mediaId")
            .retrieve()
            .toBodilessEntity()
            .awaitSingleOrNull()
            ?.headers?.contentLength ?: -1L
    } catch (e: Exception) {
        -1L
    }

    /** [downloadedBytes]
     * 다중 스레드 병렬 실행 스케줄링 기법 하에서 원자성(Atomicity)을 완벽 보장하여 데이터 레이스(경쟁 상태 및 메모리 가시성 침해) 오류 없이 안전하게 실시간 누적 다운로드 바이트 크기를 덧셈 연산 및 기록하기 위한 스레드 안전(Thread-safe) 특수 원자 변수(AtomicLong) 자료구조임.
     */
    val downloadedBytes = AtomicLong(0)

    /** [lastReportedPercent]
     * 다중 다운로드가 동시에 백그라운드 기동될 때 터미널 진행 상황 로그가 무분별하게 중첩되어 콘솔 화면이 번쩍이고 깨지는 현상을 완벽 방어하기 위해, 오직 진행 상태 백분율이 25% 경계선에 다다를 때 단 한 번만 새로운 로그가 개행되도록 분기 제어하는 영속 상태 비교용 변수임.
     */
    var lastReportedPercent = -1L

    /** [responseFlux]
     * Spring Reactor 라이브러리의 리액티브 스트림(Reactive Streams) 기반 [Flux<DataBuffer>] 파이프라인 자료구조임.
     * 대용량 멀티미디어를 한 번에 온메모리(On-Memory)로 적재하지 않고 네트워크 버퍼의 흐름 제어(Backpressure) 규격에 맞추어 시간 축에 따라 유입되는 바이너리 청크 조각(DataBuffer)들을 비동기식 이벤트 스트림으로 연속 중계 공급하는 스트리밍 파이프임.
     */
    val responseFlux = client.get()
        .uri("/api/media/stream/$mediaId")
        .accept(MediaType.valueOf(contentType))
        .retrieve()
        .bodyToFlux<DataBuffer>()
        .doOnNext { dataBuffer ->
            val size = dataBuffer.readableByteCount()
            val current = downloadedBytes.addAndGet(size.toLong())

            if (totalSize > 0) {
                val percent = current * 100 / totalSize
                if (percent % 25 == 0L && percent != lastReportedPercent) {
                    println("⏳ [ID: $mediaId] 다운로드 진행률: $percent% ($current / $totalSize bytes)")
                    lastReportedPercent = percent
                }
            }
        }

    // 3. 로컬 저장 장치 보존 쓰기 연산 수행
    try {
        /** [DataBufferUtils.write 논블로킹 파일 기록 알고리즘]
         * 네트워크 소켓에서 조각나 수신되는 Flux 바이너리 버퍼 스트림 데이터를 로컬 하드 디스크에 쓰기(Write)할 때, 커널의 비동기 입출력 채널(Asynchronous File Channel)을 은밀하게 가동하여 파일 라이팅 시 스레드가 블로킹되어 연산이 일시정지되는 병목 현상을 원천 차단하는 고성능 비차단 스토리지 보존 알고리즘임.
         */
        DataBufferUtils.write(
            responseFlux,
            outputFile.toPath(),
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        ).awaitSingleOrNull()

        println("\n🎉 [ID: $mediaId] 스트리밍 전체 다운로드 완료: ${outputFile.name}")

        /** [Desktop.getDesktop().open 네이티브 쉘 프로세스 호출 기법]
         * 자바 가상머신(JVM)의 자바 네이티브 인터페이스(JNI) 하위 시스템 호출을 활성화하여 현재 클라이언트 운영체제(Windows, MacOS 등)의 시스템 레지스트리를 스캔함.
         * 다운로드 완료된 타겟 미디어 확장자(.mp4, .mp3 등)에 디폴트 링크 설정되어 있는 OS 고유 동영상 재생 프로세스(예: Windows 미디어 플레이어)를 독립 자식 프로세스로 분기 론칭(Spawn)하여 별도의 멀티 재생 GUI 윈도우 창들을 개별 구동하는 시스템 쉘 가동 기법임.
         */
        if (Desktop.isDesktopSupported()) {
            println("🎬 [ID: $mediaId] 동영상 플레이어를 실행합니다: '${title}'")
            Desktop.getDesktop().open(outputFile)
        } else {
            println("⚠️ [ID: $mediaId] 기본 재생기 미지원 환경입니다. 파일 경로: ${outputFile.absolutePath}")
        }

    } catch (e: Exception) {
        println("\n❌ [ID: $mediaId] 백그라운드 파일 처리 중 에러 발생: ${e.message}")
    }
}