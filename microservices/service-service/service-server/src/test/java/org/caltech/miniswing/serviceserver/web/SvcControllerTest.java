package org.caltech.miniswing.serviceserver.web;

import lombok.extern.slf4j.Slf4j;
import org.caltech.miniswing.plmclient.PlmClient;
import org.caltech.miniswing.plmclient.dto.ProdResponseDto;
import org.caltech.miniswing.productclient.ProductClient;
import org.caltech.miniswing.serviceclient.dto.SvcCd;
import org.caltech.miniswing.serviceclient.dto.SvcStCd;
import org.caltech.miniswing.serviceclient.dto.ServiceStatusChangeRequestDto;
import org.caltech.miniswing.serviceserver.domain.Svc;
import org.caltech.miniswing.serviceserver.domain.SvcRepository;
import org.caltech.miniswing.serviceserver.dto.ServiceCreateRequestDto;
import org.caltech.miniswing.serviceserver.service.SvcService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@RunWith(SpringRunner.class)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"eureka.client.enabled=false","spring.cloud.config.enabled=false"}
)
@Slf4j
public class SvcControllerTest {
    @LocalServerPort
    private int port;

    @Autowired
    private SvcRepository svcRepository;

    @Autowired
    private WebTestClient client;

    @MockBean(name = "plmClient")
    private PlmClient plmClient;

    @MockBean(name = "productClient")
    private ProductClient productClient;

    @Autowired
    private SvcService svcService;

    private String urlPrefix;

    private long custNum = 1;

    private List<String> prods;

    @Before
    public void setUp() {
        prods = Arrays.asList("NA00000001", "NA00000002", "NA00000003");

        urlPrefix = "http://localhost:" + port + "/swing/api/v1/services";
    }

    @After
    public void tearDown() {
        svcRepository.deleteAll();
    }

    @Test
    public void test_서비스생성() throws Exception {
        ServiceCreateRequestDto dto = ServiceCreateRequestDto.builder()
                .svcNum("01012345667")
                .svcCd(SvcCd.C)
                .custNum(custNum)
                .feeProdId(prods.get(0))
                .build();

        client.post()
                .uri(urlPrefix)
                .accept(APPLICATION_JSON)
                .body(BodyInserters.fromValue(dto))
                .exchange()
                .expectStatus().isOk()
        ;

        assertThat(svcRepository.findAll()).hasSize(1);
    }

    @Test
    public void test_서비스정지() throws Exception {
        Svc s = subscribeSampleSvc();

        long svcMgmtNum = svcRepository.findAll().get(0).getSvcMgmtNum();

        mockPlmService(s);

        client.put()
                .uri(buildServiceStatusChangeUrl(s))
                    .accept(APPLICATION_JSON)
                .body(BodyInserters.fromValue(ServiceStatusChangeRequestDto.createSuspendServiceDto()))
                .exchange()
                    .expectStatus().isOk()
        ;

        assertThat(svcService.getService(s.getSvcMgmtNum()).block().getSvcStCd()).isEqualTo(SvcStCd.SP);
    }

    @Test
    public void test_서비스활성화() throws Exception {
        Svc s = subscribeSampleSvc();
        s.suspend(LocalDateTime.now().minusDays(3));
        svcRepository.save(s);

        mockPlmService(s);

        client.put()
                .uri(buildServiceStatusChangeUrl(s))
                    .accept(APPLICATION_JSON)
                .body(BodyInserters.fromValue(ServiceStatusChangeRequestDto.createActivateServiceDto()))
                .exchange()
                    .expectStatus().isOk()
        ;


        assertThat(svcService.getService(s.getSvcMgmtNum()).block().getSvcStCd()).isEqualTo(SvcStCd.AC);
    }

    @Test
    public void test_서비스해지() throws Exception {
        Svc s = subscribeSampleSvc();

        mockPlmService(s);

        client.put()
                .uri(buildServiceStatusChangeUrl(s))
                    .accept(APPLICATION_JSON)
                .body(BodyInserters.fromValue(ServiceStatusChangeRequestDto.createTerminateServiceDto()))
                .exchange()
                    .expectStatus().isOk()
        ;

        assertThat(svcService.getService(s.getSvcMgmtNum()).block().getSvcStCd()).isEqualTo(SvcStCd.TG);
    }

    /*
    @Test
    public void 기본요금제변경() throws Exception {
        Svc s = subscribeSampleSvc();
        String afterProdId = "NA00000003";

        given( productClient.subscribeProduct(any(ProdSubscribeRequestDto.class)) )
                .willReturn(Mono.empty().then());

        client.put()
                .uri(urlPrefix + "/" + s.getSvcMgmtNum())
                    .accept(APPLICATION_JSON)
                .body(BodyInserters.fromValue(SvcUpdateRequestDto.createChangeBasicProdDto(afterProdId)))
                .exchange()
                    .expectStatus().isOk()
        ;

        assertThat(svcService.getService(s.getSvcMgmtNum()).block().getFeeProdId()).isEqualTo(afterProdId);
    }

     */

    @Test
    public void test_서비스조회() throws Exception {
        Svc s = subscribeSampleSvc();

        String url = urlPrefix + "/" + s.getSvcMgmtNum();

        mockPlmService(s);

        client.get()
                .uri(url)
                    .accept(APPLICATION_JSON)
                .exchange()
                    .expectStatus().isOk()
                    .expectHeader().contentType(APPLICATION_JSON)
                .expectBody()
                    .jsonPath("$.svcMgmtNum").isEqualTo(s.getSvcMgmtNum())
                    .jsonPath("$.svcNum").isEqualTo(s.getSvcNum())
                    .jsonPath("$.feeProdId").isEqualTo(s.getFeeProdId())
                    .jsonPath("$.feeProdNm").isEqualTo("표준요금제");
    }

    @Test
    public void test_서비스조회_고객단위() throws Exception {
        List<Svc> svcs = subscribeSampleSvcs();

        String url = urlPrefix + "?custNum=1&offset=0&limit=10&includeTermSvc=true";

        given( plmClient.getProdNmByIds(any()) )
                .willReturn( Mono.just(Arrays.asList(
                        ProdResponseDto.builder().prodId(prods.get(0)).prodNm("표준요금제").build(),
                        ProdResponseDto.builder().prodId(prods.get(1)).prodNm("기본요금제").build()
                )));

        client.get()
                .uri(url)
                    .accept(APPLICATION_JSON)
                .exchange()
                    .expectStatus().isOk()
                    .expectHeader().contentType(APPLICATION_JSON)
                .expectBody()
                    .jsonPath("$.length()").isEqualTo(3)
                    .jsonPath("$[0].svcMgmtNum").isEqualTo(svcs.get(0).getSvcMgmtNum())
                    .jsonPath("$[0].feeProdNm").isEqualTo("표준요금제")
                    .jsonPath("$[1].svcMgmtNum").isEqualTo(svcs.get(1).getSvcMgmtNum())
                    .jsonPath("$[1].feeProdNm").isEqualTo("기본요금제")
                    .jsonPath("$[2].svcMgmtNum").isEqualTo(svcs.get(2).getSvcMgmtNum())
                    .jsonPath("$[2].feeProdNm").isEqualTo("표준요금제");
    }

    private Svc subscribeSampleSvc() {
        Svc s = Svc.builder()
                .svcCd(SvcCd.C)
                .svcStCd(SvcStCd.AC)
                .svcNum("0101234567")
                .svcScrbDt(LocalDate.now())
                .feeProdId(prods.get(0))
                .custNum(custNum)
                .build();
        s.subscribe(LocalDateTime.now());
        return svcRepository.save(s);
    }

    private List<Svc> subscribeSampleSvcs() {
        return svcRepository.saveAll(
                Arrays.asList(
                        Svc.builder()
                                .svcCd(SvcCd.C)
                                .svcStCd(SvcStCd.AC)
                                .svcNum("0101234567")
                                .svcScrbDt(LocalDate.now())
                                .feeProdId(prods.get(0))
                                .custNum(custNum)
                                .build(),
                        Svc.builder()
                                .svcCd(SvcCd.C)
                                .svcStCd(SvcStCd.AC)
                                .svcNum("0101234568")
                                .svcScrbDt(LocalDate.now())
                                .feeProdId(prods.get(1))
                                .custNum(custNum)
                                .build(),
                        Svc.builder()
                                .svcCd(SvcCd.C)
                                .svcStCd(SvcStCd.AC)
                                .svcNum("0101234569")
                                .svcScrbDt(LocalDate.now())
                                .feeProdId(prods.get(0))
                                .custNum(custNum)
                                .build()
                )
        );
    }

    private String buildServiceStatusChangeUrl(Svc s) {
        return urlPrefix + "/" + s.getSvcMgmtNum() + "/status";
    }

    private void mockPlmService(Svc s) {
        given( plmClient.getProdNmByIds(any()) )
                .willReturn( Mono.just(Collections.singletonList(
                        ProdResponseDto.builder().prodId(s.getFeeProdId()).prodNm("표준요금제").build()))
                );
    }
}


