package org.caltech.miniswing.customerserver.domain;


import org.caltech.miniswing.customerclient.dto.CustTypCd;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest(
        properties = {"eureka.client.enabled=false","spring.cloud.config.enabled=false"}
)
public class CustRepositoryTest {
    @Autowired
    CustRepository custRepository;

    @After
    public void tearDown() {
        custRepository.deleteAll();
    }

    @Test
    public void test_저장_그냥jpatest용() {
        Cust c = Cust.builder()
                .custNm("강인수")
                .custTypCd(CustTypCd.C01)
                .custRgstDt(LocalDate.now())
                .build();

        Cust savedCust = custRepository.save(c);
        assertThat(savedCust.getCustNum()).isNotEqualTo(0);
    }

    @Test
    public void test_이름과생일로고객가져오기() {
        custRepository.saveAll( TestDataFactory.createManyCusts() );
        assertThat(custRepository.findByCustNmAndBirthDtOrderByCustRgstDtDesc("강인수", LocalDate.of(1982,1,1))).hasSize(1);
        assertThat(custRepository.findByCustNmAndBirthDtOrderByCustRgstDtDesc("강인수", LocalDate.of(1983,1,1))).hasSize(0);
        assertThat(custRepository.findByCustNmAndBirthDtOrderByCustRgstDtDesc("이수경", LocalDate.of(1980,1,1))).hasSize(0);
    }
}
