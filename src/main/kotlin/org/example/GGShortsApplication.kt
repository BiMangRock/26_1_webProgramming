//22411895 김창민
package org.example

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * [애플리케이션 시작 설정 파일]
 * 스프링 부트 애플리케이션의 설정과 빈 초기화를 구동하는 메인 클래스임.
 * @SpringBootApplication 어노테이션을 통해 컴포넌트 스캔 및 자동 구성을 시작함.
 */
@SpringBootApplication
class GGShortsApplication

/**
 * [서버 최초 구동 진입 함수]
 * 애플리케이션 시작 지점으로 코틀린 탑 레벨 영역에 정의되어 단순 구조를 유지함.
 * 내부적으로 runApplication 메서드를 통해 스프링 부트 엔진을 로드하고 웹 서버를 실행함.
 */
fun main(args: Array<String>) {
    runApplication<GGShortsApplication>(*args)
    //접속 url http://localhost:8080
}
