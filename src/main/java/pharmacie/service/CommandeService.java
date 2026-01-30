package pharmacie.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.time.LocalDate;


import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Positive;
import lombok.extern.slf4j.Slf4j;
import pharmacie.dao.CommandeRepository;
import pharmacie.dao.DispensaireRepository;
import pharmacie.dao.LigneRepository;
import pharmacie.dao.MedicamentRepository;
import pharmacie.entity.Commande;
import pharmacie.entity.Ligne;
import pharmacie.entity.Medicament;

@Slf4j
@Service
@Validated // Les annotations de validation sont actives sur les méthodes de ce service
// (ex: @Positive)
public class CommandeService {
    // La couche "Service" utilise la couche "Accès aux données" pour effectuer les traitements
    private final CommandeRepository commandeDao;
    private final DispensaireRepository dispensaireDao;
    private final LigneRepository ligneDao;
    private final MedicamentRepository medicamentDao;

    // @Autowired
    // Spring initialisera automatiquement ces paramètres
    public CommandeService(CommandeRepository commandeDao, DispensaireRepository dispensaireDao, LigneRepository ligneDao, MedicamentRepository medicamentDao) {
        this.commandeDao = commandeDao;
        this.dispensaireDao = dispensaireDao;
        this.ligneDao = ligneDao;
        this.medicamentDao = medicamentDao;
    }

    /**
     * Service métier : Enregistre une nouvelle commande pour un dispensaire connu par sa clé
     * Règles métier :
     * - le dispensaire doit exister
     * - On initialise l'adresse de livraison avec l'adresse du dispensaire
     * - Si le dispensaire a déjà commandé plus de 100 articles, on lui offre une remise de 15%
     *
     * @param dispensaireCode la clé du dispensaire
     * @return la commande créée
     * @throws java.util.NoSuchElementException si le dispensaire n'existe pas
     */
    @Transactional
    public Commande creerCommande(@NonNull String dispensaireCode) {
        log.info("Service : Création d'une commande pour {}", dispensaireCode);
        // On vérifie que le dispensaire existe
        var dispensaire = dispensaireDao.findById(dispensaireCode).orElseThrow();
        // On crée une commande pour ce dispensaire
        var nouvelleCommande = new Commande(dispensaire);
        // On initialise l'adresse de livraison avec l'adresse du dispensaire
        nouvelleCommande.setAdresseLivraison(dispensaire.getAdresse());
        // Si le dispensaire a déjà commandé plus de 100 médicaments, on lui offre une remise de 15%
        // La requête SQL nécessaire est définie dans l'interface DispensaireRepository
        var nbArticles = dispensaireDao.nombreArticlesCommandesPar(dispensaireCode);
        if (nbArticles > 100) {
            nouvelleCommande.setRemise(new BigDecimal("0.15"));
        }
        // On enregistre la commande (génère la clé)
        commandeDao.save(nouvelleCommande);
        return nouvelleCommande;
    }

    /**
     * <pre>
     * Service métier :
     * Enregistre une nouvelle ligne de commande pour une commande connue par sa clé,
     * Incrémente la quantité totale commandée (Medicament.unitesCommandees) avec la quantite à commander
     * Règles métier :
     * - le médicament référencé doit exister et ne pas être indisponible
     * - la commande doit exister
     * - la commande ne doit pas être déjà envoyée (le champ 'envoyeele' doit être null)
     * - la quantité doit être positive
     * - La quantité en stock du médicament ne doit pas être inférieure au total des quantités commandées
     * - Si le médicament est déjà présent dans la commande, les quantités sont additionnées
     * <pre>
     *
     * @param commandeNum la clé de la commande
     * @param medicamentRef  la clé du médicament
     * @param quantite    la quantité commandée (positive)
     * @return la ligne de commande créée
     * @throws java.util.NoSuchElementException                si la commande ou le
     *                                                         médicament n'existe pas
     * @throws IllegalStateException                           si il n'y a pas assez
     *                                                         de stock, si la
     *                                                         commande a déjà été
     *                                                         envoyée, ou si le
     *                                                         médicament est
     *                                                         indisponible
     * @throws jakarta.validation.ConstraintViolationException si la quantité n'est
     *                                                         pas positive
     */
    @Transactional
    public Ligne ajouterLigne(int commandeNum, int medicamentRef, @Positive int quantite) {
        // Récupère et vérifie la commande
        Commande commande = getCommande(commandeNum);
        if (commande.getEnvoyeele() != null) {
            throw new IllegalStateException("La commande a déjà été envoyée");
        }
        // Récupère et vérifie le médicament
        Medicament medicament = medicamentDao.findById(medicamentRef).orElseThrow();
        if (medicament.isIndisponible()) {
            throw new IllegalStateException("Le médicament est indisponible");
        }
        // Vérifie le stock global : on ne doit pas dépasser les unités en stock
        long totalApresAjout = (long) medicament.getUnitesCommandees() + (long) quantite;
        if (medicament.getUnitesEnStock() < totalApresAjout) {
            throw new IllegalStateException("Pas assez de stock pour ce médicament");
        }
        // Si le médicament est déjà présent dans la commande, on additionne les quantités
        for (Ligne l : commande.getLignes()) {
            if (l.getMedicament() != null && l.getMedicament().getReference().equals(medicamentRef)) {
                l.setQuantite(l.getQuantite() + quantite);
                medicament.setUnitesCommandees(medicament.getUnitesCommandees() + quantite);
                medicamentDao.save(medicament);
                ligneDao.save(l);
                commandeDao.save(commande);
                return l;
            }
        }
        // Sinon, création d'une nouvelle ligne
        Ligne nouvelleLigne = new Ligne(commande, medicament, quantite);
        medicament.setUnitesCommandees(medicament.getUnitesCommandees() + quantite);
        medicamentDao.save(medicament);
        ligneDao.save(nouvelleLigne);
        return nouvelleLigne;
    }

    /**
     * <pre>
     * Service métier :
     * Supprime une ligne de commande pour une commande connue par sa clé,
     * Décrémente la quantité totale commandée (Medicament.unitesCommandees) de la quantité commandée
     * Règles métier :
     * - la commande ne doit pas être déjà envoyée (le champ 'envoyeele' doit être null)
     * <pre>
     *
     * @param id la clé de la ligne
     * @throws IllegalStateException si la commande a déjà été envoyée
     */
    @Transactional
    public void supprimerLigne(int id) {
        log.info("Service : Suppression de la ligne de commande avec id {}", id);
        // On recherche la ligne dans l'ensemble des commandes (sans utiliser ligneDao)
        for (Commande commande : commandeDao.findAll()) {
            Ligne trouve = null;
            for (Ligne l : commande.getLignes()) {
                if (l.getId() != null && l.getId().equals(id)) {
                    trouve = l;
                    break;
                }
            }
            if (trouve != null) {
                // On vérifie que la commande n'a pas déjà été envoyée
                if (commande.getEnvoyeele() != null) {
                    throw new IllegalStateException("La commande a déjà été envoyée");
                }
                // Décrémente les unités en commande du médicament
                Medicament med = trouve.getMedicament();
                med.setUnitesCommandees(med.getUnitesCommandees() - trouve.getQuantite());
                if (med.getUnitesCommandees() < 0) {
                    med.setUnitesCommandees(0); // sécurité
                }
                // Retire la ligne de la commande; orphanRemoval supprimera l'entité Ligne
                commande.getLignes().remove(trouve);
                medicamentDao.save(med);
                commandeDao.save(commande);
                return;
            }
        }
        throw new NoSuchElementException("Ligne non trouvée");
    }

    /**
     * Service métier : Enregistre l'expédition d'une commande connue par sa clé
     * Règles métier :
     * - la commande doit exister
     * - la commande ne doit pas être déjà envoyée (le champ 'envoyeele' doit être null)
     * - On renseigne la date d'expédition (envoyeele) avec la date du jour
     * - Pour chaque médicament dans les lignes de la commande :
     * décrémente la quantité en stock (Medicament.unitesEnStock) de la quantité dans la commande
     * décrémente la quantité commandée (Medicament.unitesCommandees) de la quantité dans la commande
     *
     * @param commandeNum la clé de la commande
     * @return la commande mise à jour
     * @throws java.util.NoSuchElementException si la commande n'existe pas
     * @throws IllegalStateException            si la commande a déjà été envoyée
     */
    @Transactional
    public Commande enregistreExpedition(int commandeNum) {
        Commande commande = getCommande(commandeNum);
        if (commande.getEnvoyeele() != null) {
            throw new IllegalStateException("La commande a déjà été envoyée");
        }
        commande.setEnvoyeele(LocalDate.now());
        commandeDao.save(commande);
        for (Ligne ligne : commande.getLignes()) {
            Medicament medicament = ligne.getMedicament();
            medicament.setUnitesEnStock(medicament.getUnitesEnStock() - ligne.getQuantite());
            medicament.setUnitesCommandees(medicament.getUnitesCommandees() - ligne.getQuantite());
            medicamentDao.save(medicament);
        }
        return commande;
    }

    /**
     * Service métier : Récupère une commande connue par sa clé
     *
     * @param commandeNum la clé de la commande
     * @return la commande
     * @throws java.util.NoSuchElementException si la commande n'existe pas
     */
    @Transactional
    public Commande getCommande(int commandeNum) {
        Commande c = commandeDao.findById(commandeNum).orElseThrow();
        // Force l'initialisation des lignes associées et des médicaments pour éviter LazyInitializationException
        c.getLignes().forEach(l -> {
            l.getQuantite();
            if (l.getMedicament() != null) {
                l.getMedicament().getReference();
            }
        });
        return c;
    }

    @Transactional
    public List<Commande> getCommandeEnCoursPour(String dispensaireCode) {
        return commandeDao.commandesEnCoursPour(dispensaireCode);
    }

    public Medicament getMedicament(int medicamentRef) {
        return medicamentDao.findById(medicamentRef).orElse(null);
    }
}
