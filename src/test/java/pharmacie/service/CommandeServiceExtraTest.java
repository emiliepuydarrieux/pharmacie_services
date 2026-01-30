package pharmacie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import pharmacie.entity.Commande;
import pharmacie.entity.Ligne;
import pharmacie.entity.Medicament;

@SpringBootTest
@Transactional
public class CommandeServiceExtraTest {

    @Autowired
    private CommandeService service;

    @Test
    void enregistreExpedition_decrementsStockAndCommandesAndSetsEnvoyeele() {
        Commande cBefore = service.getCommande(99998);
        assertThat(cBefore.getEnvoyeele()).isNull();

        Map<Integer, Integer> beforeStock = new HashMap<>();
        Map<Integer, Integer> beforeCommanded = new HashMap<>();
        for (Ligne l : cBefore.getLignes()) {
            Medicament m = l.getMedicament();
            beforeStock.put(m.getReference(), m.getUnitesEnStock());
            beforeCommanded.put(m.getReference(), m.getUnitesCommandees());
        }

        service.enregistreExpedition(99998);

        Commande cAfter = service.getCommande(99998);
        assertThat(cAfter.getEnvoyeele()).isNotNull();

        for (Ligne l : cAfter.getLignes()) {
            Medicament m = service.getMedicament(l.getMedicament().getReference());
            assertThat(m.getUnitesEnStock()).isEqualTo(beforeStock.get(m.getReference()) - l.getQuantite());
            assertThat(m.getUnitesCommandees()).isEqualTo(beforeCommanded.get(m.getReference()) - l.getQuantite());
        }

        // Cannot register shipment twice
        assertThrows(IllegalStateException.class, () -> service.enregistreExpedition(99998));
    }

    @Test
    void getCommandeEnCoursPour_returnsOnlyNonSent() {
        List<Commande> enCours = service.getCommandeEnCoursPour("2COM");
        assertThat(enCours).extracting(Commande::getNumero).contains(99998).doesNotContain(99999);

        List<Commande> none = service.getCommandeEnCoursPour("0COM");
        assertThat(none).isEmpty();
    }

    @Test
    void getMedicament_returnsMedicamentOrNull() {
        assertThat(service.getMedicament(98)).isNotNull();
        assertThat(service.getMedicament(9999)).isNull();
    }
}
