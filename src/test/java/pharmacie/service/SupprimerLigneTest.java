package pharmacie.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import pharmacie.dao.CommandeRepository;
import pharmacie.dao.LigneRepository;
import pharmacie.dao.MedicamentRepository;
import pharmacie.entity.Commande;
import pharmacie.entity.Ligne;
import pharmacie.entity.Medicament;

@SpringBootTest
class SupprimerLigneTest {

    @Autowired
    private CommandeService service;
    @Autowired
    private CommandeRepository commandeDao;
    @Autowired
    private MedicamentRepository medicamentDao;
    @Autowired
    private LigneRepository ligneDao;

    @Test
    void supprimerLigne_decrementeUnitesCommandeesEtRetireLaLigne() {
        Commande c = service.getCommande(99998);
        Ligne ligne = c.getLignes().stream().filter(l -> l.getMedicament().getReference() == 98).findFirst().orElseThrow();
        int id = ligne.getId();
        Medicament medAvant = medicamentDao.findById(98).orElseThrow();
        int before = medAvant.getUnitesCommandees();
        int qte = ligne.getQuantite();

        service.supprimerLigne(id);

        Medicament medApres = medicamentDao.findById(98).orElseThrow();
        assertEquals(before - qte, medApres.getUnitesCommandees(), "les unités en commande doivent être décrémentées");
        // Vérifier que la ligne n'existe plus dans la BDD
        assertTrue(ligneDao.findById(id).isEmpty(), "la ligne doit avoir été supprimée");
    }

    @Test
    void supprimerLigne_commandeEnvoyee_genereIllegalStateException() {
        Commande c = service.getCommande(99999);
        Ligne ligne = c.getLignes().stream().findFirst().orElseThrow();
        int id = ligne.getId();
        assertThrows(IllegalStateException.class, () -> service.supprimerLigne(id));
    }
}
