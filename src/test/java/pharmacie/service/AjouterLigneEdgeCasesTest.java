package pharmacie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import jakarta.validation.ConstraintViolationException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import pharmacie.dao.MedicamentRepository;
import pharmacie.entity.Medicament;

@SpringBootTest
@Transactional
public class AjouterLigneEdgeCasesTest {

    @Autowired
    private CommandeService service;

    @Autowired
    private MedicamentRepository medicamentDao;

    @Test
    void ajouterLigne_quantiteZero_throwsConstraintViolation() {
        assertThrows(ConstraintViolationException.class, () -> service.ajouterLigne(99998, 98, 0));
    }

    @Test
    void ajouterLigne_addRemainingStock_succeeds() {
        Medicament mBefore = medicamentDao.findById(98).orElseThrow();
        int beforeUnits = mBefore.getUnitesCommandees();
        int remaining = mBefore.getUnitesEnStock() - beforeUnits;

        service.ajouterLigne(99998, 98, remaining);

        Medicament mAfter = medicamentDao.findById(98).orElseThrow();
        assertThat(mAfter.getUnitesCommandees()).isEqualTo(beforeUnits + remaining);
    }

    @Test
    void ajouterLigne_concurrentAdds_sumNotExceedStock() throws Exception {
        // Use medicament 98 which is already present in commande 99998 to avoid unique index insert race
        Medicament mBefore = medicamentDao.findById(98).orElseThrow();
        int before = mBefore.getUnitesCommandees();
        int add1 = 30, add2 = 39; // sum = 69 (remaining stock)

        ExecutorService ex = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<Void> t1 = () -> {
            ready.countDown();
            start.await(5, TimeUnit.SECONDS);
            service.ajouterLigne(99998, 98, add1);
            return null;
        };
        Callable<Void> t2 = () -> {
            ready.countDown();
            start.await(5, TimeUnit.SECONDS);
            service.ajouterLigne(99998, 98, add2);
            return null;
        };

        Future<Void> f1 = ex.submit(t1);
        Future<Void> f2 = ex.submit(t2);
        // Wait until both threads are ready, then release
        ready.await(5, TimeUnit.SECONDS);
        start.countDown();

        int successfulSum = 0;
        try {
            f1.get(10, TimeUnit.SECONDS);
            successfulSum += add1;
        } catch (Exception e) {
            // If cause is IllegalStateException due to insufficient stock, count as failed
            Throwable cause = e.getCause();
            if (!(cause instanceof IllegalStateException)) {
                throw e; // rethrow unexpected exceptions
            }
        }
        try {
            f2.get(10, TimeUnit.SECONDS);
            successfulSum += add2;
        } catch (Exception e) {
            Throwable cause = e.getCause();
            if (!(cause instanceof IllegalStateException)) {
                throw e;
            }
        }

        ex.shutdown();
        ex.awaitTermination(5, TimeUnit.SECONDS);

        Medicament mAfter = medicamentDao.findById(98).orElseThrow();
        assertThat(mAfter.getUnitesCommandees()).isEqualTo(before + successfulSum);
    }
}
