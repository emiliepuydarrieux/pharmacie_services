package pharmacie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import pharmacie.dao.MedicamentRepository;
import pharmacie.entity.Commande;
import pharmacie.entity.Ligne;
import pharmacie.entity.Medicament;

@SpringBootTest
@Transactional
public class AjouterLigneTest {

    @Autowired
    private CommandeService service;

    @Autowired
    private MedicamentRepository medicamentDao;

    @Test
    void ajouterLigne_mergeAndIncrementUnitesCommandees() {
        // commande 99998 contient déjà une ligne pour medicament 98 quantite 16
        Commande cBefore = service.getCommande(99998);
        Ligne lBefore = cBefore.getLignes().stream().filter(l -> l.getMedicament().getReference().equals(98)).findFirst().orElseThrow();
        int qBefore = lBefore.getQuantite();
        Medicament mBefore = medicamentDao.findById(98).orElseThrow();
        int unitsBefore = mBefore.getUnitesCommandees();

        service.ajouterLigne(99998, 98, 3);

        Commande cAfter = service.getCommande(99998);
        Ligne lAfter = cAfter.getLignes().stream().filter(l -> l.getMedicament().getReference().equals(98)).findFirst().orElseThrow();
        Medicament mAfter = medicamentDao.findById(98).orElseThrow();

        assertThat(lAfter.getQuantite()).isEqualTo(qBefore + 3);
        assertThat(mAfter.getUnitesCommandees()).isEqualTo(unitsBefore + 3);
    }

    @Test
    void ajouterLigne_commandeEnvoyee_throwsIllegalStateException() {
        // commande 99999 est envoyée
        assertThrows(IllegalStateException.class, () -> service.ajouterLigne(99999, 98, 1));
    }

    @Test
    void ajouterLigne_notEnoughStock_throwsIllegalStateException() {
        // medicament 98 a unitesEnStock 95 et unitesCommandees 26, essayer d'ajouter 70 doit échouer
        assertThrows(IllegalStateException.class, () -> service.ajouterLigne(99998, 98, 70));
    }

    @Test
    void ajouterLigne_indisponible_throwsIllegalStateException() {
        // medicament 97 est indisponible
        assertThrows(IllegalStateException.class, () -> service.ajouterLigne(99998, 97, 1));
    }

    @Test
    void ajouterLigne_medicamentAbsent_throwsNoSuchElement() {
        assertThrows(NoSuchElementException.class, () -> service.ajouterLigne(99998, 9999, 1));
    }
}
