# SUIVI — QR Studio

## 🧭 Ligne directrice
> **MàJ : 2026-06-07** — App Android « QR Studio » : tout transformer en QR code + lecteur caméra + historique. **État : v1 + lot d'améliorations « perso/amis » + passe d'audit (sécurité/simplification) terminés. Audit 2026-06-07 : 3 correctifs sécurité (bombe Deflate bornée, mots de passe WiFi exclus du backup + presse-papiers sensible) et refactor maintenabilité (state-holder unique du formulaire Créer, QrEncoder.styleFor, dédup downsample/permission). Revue de compilation passée (1 erreur trouvée et corrigée).** Prochaine priorité : **compiler sur le PC Windows** (`gradlew.bat assembleDebug` + `testDebugUnitTest`) puis tester sur appareil.

## ✅ Fait
- [x] Cadrage technique (versions alignées sur AlcoLimit : AGP 9.1.1, Kotlin 2.1.20, Gradle 9.3.1, Compose BOM 2025.01.00, compileSdk 36, minSdk 26, Java 17)
- [x] Arborescence + wrapper Gradle réutilisé
- [x] Build & config : settings/build/libs.versions.toml/gradle.properties, Manifest (CAMERA + FileProvider), ressources (thème, strings, icône adaptive, file_paths, backup)
- [x] Cœur : `QrType` (détection), `QrPayload` (URL/WiFi/vCard/mailto/tel/sms/geo), `QrEncoder` (ZXing + logo optionnel), `QrDecoder` (image + frames YUV), `FileEmbedding` (base64 + compression image)
- [x] Données : `HistoryRepository` (persistance JSON org.json, StateFlow, sans Room/DataStore)
- [x] Utils : `QrSharing` (copier/partager/galerie MediaStore), `QrActions` (actions contextuelles + parse/connexion WiFi), `Formatting`
- [x] Thème Compose (palette sombre/claire custom, pas de hex inline) + typographie
- [x] Navigation bottom bar 3 onglets (Scanner / Créer / Historique) — Scanner = écran d'accueil
- [x] Écran Créer (9 types, formulaires, import image/fichier, aperçu + actions)
- [x] Écran Scanner (CameraX preview + analyse ZXing + lampe + import galerie + permission)
- [x] Écran Historique (liste, filtres, suppression, dialogue tout effacer)
- [x] Bottom sheet de résultat partagée (scan + historique)
- [x] Tests unitaires (`QrTypeTest`, `QrPayloadTest`)
- [x] Revue compilation/correctness (sous-agent android-reviewer) → tout vérifié OK
- [x] Passe d'optimisation (audit android-reviewer + bug-hunter) : code mort supprimé, doublons fusionnés, correctifs de courses/fuites (voir historique git/notes)
- [x] **Lot améliorations 2026-06-06** (usage perso/amis, Material You écarté volontairement) :
  - [x] Scan : vibration au scan réussi (80 ms, permission VIBRATE), pinch-to-zoom + tap-to-focus (CameraX), décodage de 13 formats (QR, Data Matrix, Aztec, PDF417, EAN/UPC, Code 39/93/128, ITF, Codabar)
  - [x] Historique : recherche texte (contenu + label), épinglage (tri épinglés d'abord, icône PushPin), export/import JSON via SAF (merge dédupliqué par id et (contenu, origine))
  - [x] Édition : « Réutiliser dans Créer » depuis la sheet de résultat (scan + historique) — `QrParser` (parsing inverse payload→formulaire, pur JVM) + `EditRequest` hoisté dans `QrApp`
  - [x] Couleurs : 6 presets d'encre du QR (noir, violet, cyan, bleu, vert, framboise), persistées dans l'historique (`foregroundArgb`), réaffichage fidèle
  - [x] Widget écran d'accueil (RemoteViews, sans Glance) : QR épinglé le plus récent, synchro auto via collect du StateFlow dans `QrStudioApp`
  - [x] App shortcut « Scanner » (appui long sur l'icône) : `singleTask` + `onNewIntent` → navigation directe vers l'onglet Scanner
  - [x] Tests unitaires `QrParserTest` (round-trips WiFi/mailto/SMS/geo/vCard, échappements)
  - [x] Revue croisée android-reviewer + bug-hunter sur l'ensemble du lot + corrections
- [x] **Audit 2026-06-07** (sécurité + simplification, bug-hunter + android-reviewer) :
  - [x] **S1** — Bombe de décompression Deflate : `Compression.inflate` borne sa sortie (16 Mo, retourne `null` au-delà), `FileContainer.decode` propage, `HistoryRepository` ignore les entrées importées > 8192 car., `QrResultSheet` décode hors thread principal (`produceState`/`Dispatchers.Default`). Test `CompressionTest`.
  - [x] **S2** — `history.json` (mots de passe WiFi en clair) exclu du backup auto (`backup_rules.xml` + `data_extraction_rules.xml` en `exclude`). Le transfert d'historique reste l'export/import JSON manuel.
  - [x] **S3** — `QrSharing.copyToClipboard(sensitive)` pose `EXTRA_IS_SENSITIVE` (API 33+) ; appels WiFi marqués sensibles.
  - [x] **C1/C6** — `GenerateFormState` (state-holder unique + `Saver`) remplace les 18 `rememberSaveable` + le miroir x4 ; `buildPayload`/`buildLabel` prennent le form ; composables de formulaire extraits dans `GenerateForm.kt`.
  - [x] **C2/C3/C4/C5/C7** — champ mort `FileOutcome.Ready.label` retiré ; `QrEncoder.styleFor` (règle FILE→L unique, 3 sites) ; `sampleSizeFor` partagé (scan/embedding) ; `rememberLegacyStorageSave` (permission stockage mutualisée) ; `openUri`/`joinWifi` en `private`.
  - [x] Revue de compilation (android-reviewer) : 1 erreur `RETURN_IN_FUNCTION_WITH_EXPRESSION_BODY` (FileContainer) corrigée, reste validé.

## ⏳ Reste à faire
- [ ] **Compiler sur PC Windows** (Java/Gradle absents sur la VM macOS) : `gradlew.bat assembleDebug` puis `gradlew.bat testDebugUnitTest`
- [ ] Tester sur appareil réel : scan caméra (zoom/focus/vibration), connexion WiFi (Android 11+), enregistrement galerie (Android ≤9 = demande de permission), widget (épingler une entrée puis poser le widget), shortcut Scanner, export/import d'historique entre deux appareils
- [ ] Vérifier que l'écran Créer tient toujours sans scroll sur petit écran (la rangée « Couleur du QR » ajoute ~36 dp) — sinon réduire les pastilles ou les fusionner avec la ligne logo
- [ ] (Optionnel) Icône PNG 512×512 pour la fiche Play Store

## 📋 Notes / gotchas
- **Java/Gradle absents sur la VM macOS** → compilation finale sur PC Windows (Android Studio). Tests unitaires JVM purs lançables là-bas.
- **Capacité QR limitée** (~2,9 Ko max, ~2 Ko après base64) → fichiers/images : seuls les petits passent ; avertissement explicite au-delà. Comportement honnête, pas de magie.
- **WiFi auto-connect restreint** depuis Android 10 → on parse, on copie le mot de passe et on ouvre les réglages WiFi plutôt que de promettre une connexion auto.
- **Pas de DI, pas de Room/DataStore/Retrofit** (conventions APP). Persistance = fichier JSON maison.
- **Plugin `kotlin.compose` uniquement** (jamais `kotlin-android`, conflit).
- **Logo unique** : `ic_app_mark.xml` (repère « viseur de scan ») sert au launcher, au splash et au centre des QR générés (`LogoRenderer`). Forme volontairement non-QR.
- **Logo au centre + capacité** : logo ⇒ correction H (capacité réduite). Si trop dense, `GenerateViewModel` régénère sans logo et l'indique (`note`).
- **Écran Créer = page unique sans scroll** : bascule édition ⇄ résultat. `imePadding()` pour le clavier. La rangée « Couleur du QR » est masquée pour le type Fichier (encre forcée noire + ECL L : marge de contraste).
- **`FlowRow` = `@ExperimentalLayoutApi`**, `ModalBottomSheet` = `@OptIn(ExperimentalMaterial3Api::class)` (BOM 2025.01.00).
- **Capacité fichier/image maximisée** : conteneur `qrsf:1:<flag>:<mime>:<base64>` + Deflate, ECL L forcé, plafond sur la longueur du payload (≤ 2900).
- **Le scan d'un `qrsf:`/`data:` propose « Enregistrer le fichier »** (`FileExport` → Téléchargements/QR Studio).
- **Lot 2026-06-06** :
  - **Material You écarté** (décision utilisateur) : la palette violet/cyan est l'identité de l'app.
  - **`QrParser` est volontairement pur JVM** (aucun import Android) pour rester testable en unit tests ; `QrActions.parseWifi` a été déplacé dedans (avec le champ `hidden` réintroduit pour le pré-remplissage).
  - **Édition sans event bus** : `EditRequest` hoisté dans `QrApp` (état Compose), consommé par `GenerateScreen` via `LaunchedEffect` + `onEditConsumed`. Les payloads FILE et MECARD ne sont pas éditables (toForm → null → bouton masqué).
  - **Widget sans Glance** (pas de nouvelle dépendance) : RemoteViews + layout XML classique. Synchro via collect de `HistoryRepository.items` dans `QrStudioApp` (distinctUntilChanged sur l'entrée épinglée). `HistoryRepository.init` est idempotent → appelable depuis le broadcast du widget (process froid).
  - **Couleur d'encre stockée seulement si ≠ noir** (clé JSON `foreground`), jamais pour FILE (contraste max requis pour les payloads denses).
  - **Shortcut** : action custom `com.qrstudio.action.SCAN`, `launchMode="singleTask"` → `onNewIntent` incrémente `scanRequestCount` (mutableIntStateOf) → `LaunchedEffect` dans `QrApp` navigue vers Scanner.
  - **Export historique** : `openOutputStream(uri, "wt")` (truncate) sinon un fichier réécrit plus court garde des octets résiduels ; fallback `"w"` (certains providers SAF type Drive refusent `"wt"`).
  - **Correctifs de revue (android-reviewer + bug-hunter)** : écriture historique atomique (tmp+rename) + parsing par entrée (sinon un octet corrompu effaçait tout) ; `suspendCancellableCoroutine` pour le provider CameraX (sinon fuite caméra si on quitte l'onglet pendant l'init) ; `bindToLifecycle` protégé (appareils sans caméra arrière) ; intent du shortcut consommé seulement si `savedInstanceState == null` (sinon re-navigation vers Scanner à chaque rotation) ; `sendSms` passe par `QrParser` (les payloads `sms:` étaient cassés) ; WEP exclu de `WifiNetworkSuggestion` (l'API ne le supporte pas) ; lecture des fichiers choisis bornée à 20 Mo (anti-OOM) ; vCards avec champs non couverts (ADR/NOTE/TITLE/URL, TEL/EMAIL multiples) non éditables pour ne rien perdre en silence ; TRY_HARDER réservé au décodage galerie (trop lent par frame caméra) ; `pinnedAt` pour que le widget montre le dernier épinglé (pas le plus récent par création).
- **Audit 2026-06-07** :
  - **Bombe de décompression** : tout `Inflater` sur entrée non fiable (`qrsf:` scanné ou importé) doit borner sa SORTIE avant d'allouer (Deflate ≈ 1000:1). Borne par défaut 16 Mo dans `Compression.inflate`.
  - **Tout contenu scanné = entrée hostile** : le tap d'une entrée d'historique importée décode base64 + inflate → décodage **hors thread principal** (`produceState`) + borne de taille à l'import (`MAX_CONTENT_CHARS`).
  - **Backup Android** : `history.json` contient des mots de passe WiFi en clair → `exclude` dans `backup_rules.xml` ET `data_extraction_rules.xml` (les deux : `<include>` prime sinon).
  - **Presse-papiers** : `ClipDescription.EXTRA_IS_SENSITIVE` (API 33+) masque le mot de passe dans l'aperçu système ; passer par `clip.description.extras = PersistableBundle()`.
  - **Piège Kotlin** : `return` est INTERDIT dans une fonction à corps-expression (`fun f() = try {...}`) → `RETURN_IN_FUNCTION_WITH_EXPRESSION_BODY`. Utiliser un corps-bloc, ou rester en expression sans `return` (`if (x == null) null else …`).
  - **Anti-miroir du formulaire** : `GenerateFormState` (state-holder + `listSaver`) centralise les 18 champs ; `buildPayload`/`TypeForm`/le prefill ne les listent plus en parallèle. Ajouter un champ = 1 seul endroit (+ 1 ligne save/restore du Saver, ordre à respecter).
