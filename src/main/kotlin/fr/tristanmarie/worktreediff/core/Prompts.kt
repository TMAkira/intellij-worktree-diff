package fr.tristanmarie.worktreediff.core

enum class PromptLanguage { EN, FR }

/** The wording of every prompt, in one language. */
internal interface Strings {
    fun continueTitle(name: String): String
    fun archiveTitle(name: String): String
    val worktreeTitle: String
    val worktree: String
    val branch: String
    fun position(ahead: Int, behind: Int, base: String): String
    fun filesChanged(count: Int, base: String): String
    val change: String
    fun tasks(done: Int, total: Int): String
    val execMode: String
    val remaining: String
    fun andMore(count: Int): String
    val noChange: String
    fun changesOnBranch(names: String): String
    val stayInWorktree: String
    fun applyWithAria(skill: String): String
    val applyPlain: String
    fun archiveWithAria(skill: String): String
    val archivePlain: String
    val allChecked: String
    fun readFirst(base: String): String
    val newWorkTitle: String
    val repository: String
    val baseBranch: String
    fun activeChanges(names: String): String
    val noActiveChange: String
    val interview: String
    fun proposeWithAria(skill: String): String
    val proposePlain: String
    val proposeBare: String
    val stayOnMain: String
    fun handOff(button: String): String
    /** One line the plugin guarantees above any prompt: who asked, and how to trust the address. */
    fun reportTo(who: String): String
    val recordSession: String
    fun observability(change: String, worktree: String, branch: String, base: String, specSession: String?): List<String>
    val delegation: List<String>
    fun launchRequest(dir: String): List<String>
    fun handOver(dir: String): List<String>
}

private object EN : Strings {
    override fun continueTitle(name: String) = "Continue the OpenSpec change \"$name\"."
    override fun archiveTitle(name: String) = "Archive the completed OpenSpec change \"$name\"."
    override val worktreeTitle = "Work on the branch checked out in this worktree."
    override val worktree = "Worktree"
    override val branch = "Branch"
    override fun position(ahead: Int, behind: Int, base: String) = "Position: $ahead ahead / $behind behind $base"
    override fun filesChanged(count: Int, base: String) = "Files changed vs $base: $count"
    override val change = "OpenSpec change"
    override fun tasks(done: Int, total: Int) = "Tasks: $done/$total done"
    override val execMode = "Exec mode"
    override val remaining = "Remaining tasks:"
    override fun andMore(count: Int) = "- …and $count more in tasks.md"
    override val noChange = "No OpenSpec change on this branch."
    override fun changesOnBranch(names: String) = "OpenSpec changes on this branch: $names"
    override val stayInWorktree = "Work in that worktree, not in the main checkout."
    override fun applyWithAria(skill: String) = "Read tasks.md and any aria-meta.md first, then use the $skill workflow."
    override val applyPlain = "Read tasks.md first, then continue the change, ticking tasks as they land."
    override fun archiveWithAria(skill: String) = "Then use the $skill workflow."
    override val archivePlain = "Then move the change directory under openspec/changes/archive/."
    override val allChecked = "Every task is checked. Verify that the work really landed before archiving."
    override fun readFirst(base: String) =
        "Start by reading the diff against $base to see what the branch already does, then tell me " +
            "what you find before changing anything."
    override val newWorkTitle = "Frame a new piece of work as an OpenSpec change."
    override val repository = "Repository"
    override val baseBranch = "Base branch"
    override fun activeChanges(names: String) = "Active changes, do not duplicate one of them: $names"
    override val noActiveChange = "No active change in this repository yet."
    override val interview =
        "Ask me what I need before writing anything — use AskUserQuestion, one question at a time, " +
            "and keep asking until the scope, the constraints and what is out of scope are clear. Do " +
            "not assume, and do not start from a guess of what I probably meant."
    override fun proposeWithAria(skill: String) = "Then run the $skill workflow to produce the change."
    override val proposePlain =
        "Then create the change with `openspec new change \"<kebab-case-name>\"` and write its " +
            "artifacts: proposal.md, design.md when the shape is not obvious, tasks.md."
    override val proposeBare =
        "If the openspec CLI is not available, create openspec/changes/<kebab-case-name>/ by hand, " +
            "with a proposal.md and a tasks.md of checkbox tasks, matching the other changes here."
    override val stayOnMain =
        "Work in the main checkout and stop there: do not create a branch, do not create a worktree, " +
            "do not commit. The change being uncommitted is exactly what marks it as a draft."
    override fun handOff(button: String) =
        "When the change is written, tell me its name and stop. I read the proposal, then the " +
            "\"$button\" button in the DevFlow tool window creates the branch, the worktree and the first commit."
    override fun reportTo(who: String) =
        "Report to $who, the session that asked for this launch. Make it name this change before you trust that address: session names are recycled, and the stranger who inherits one answers politely."
    override fun observability(change: String, worktree: String, branch: String, base: String, specSession: String?): List<String> {
        val identity = "change $change, worktree $worktree, branch $branch, base $base"
        val reading =
            "\"read <file> in this worktree and tell me whether X\" is what triggered every real finding measured between 2026-08-31 and 09-03"
        val known = listOf(
            "Before writing a single line, message $specSession - the session that asked for this launch, and so the one that wrote this spec - so it can observe this work. Establish that it IS that session before you rely on it: ask it to name this change, and wait for the answer. Session names are recycled, so an address can now resolve to a session that never saw this spec - measured, 2026-08-31, and the stranger answers politely, which is what makes it dangerous. If the wrong one answers, say so and drop the address; do not go looking for a replacement audience.",
            "- Give it: $identity. Add the exact gate commands, where test reports land, and your throwaway database if any.",
            "- Ask it for readings, not for a watch: $reading. A commit Monitor is available to it - polling this worktree every 60s, with a seen-hashes state file - and it may arm one; but over the two days it was armed it produced 0 findings out of 178, while the peers were already announcing their commits by message. It added nothing to that announcement, and it stays blind for as long as nothing is committed. Whatever it arms, NOT notify_when_idle: measured, it delivers nothing.",
        )
        val anonymous = listOf(
            "No session is recorded as watching this work: this launch carried no requester. Do not go looking for one - measured on 2026-09-01/02, the prescription to broadcast to the sessions of this repository produced 4 byte-identical messages and 7 hedging clauses, against 4 and 5 in the days before it existed: no improvement. Leave the trail where a reader will find it instead:",
            "- Open every message you send, and every answer you give, with one line: $identity. Over those same two days the addressing noise was 3 messages out of 55, all inside the first three minutes; an opening line is what removes it, not a search for the right address.",
            "- When a session does contact you, make it name this change before you trust the address, then treat it as your observer: give it the exact gate commands, where test reports land, and your throwaway database if any. Ask it for readings rather than for a watch - $reading.",
        )
        return listOf("") + (if (specSession != null) known else anonymous) + listOf(
            "- A peer agreeing with you is not a measurement. Measured 2026-09-03: two sessions held a false consensus for 2 h 25 and it reached the body of a PR; what broke it was a question from the user, not the review, and what refuted it was already in the repository. Agreement means \"go and get the number\", never \"confirmed\".",
            "- Commit each task section as soon as it is finished and measured. An observer only sees commits, so its silence means \"nothing committed\", never \"all is well\".",
            "- EVERY measurement is written to a file whose path you give, or it does not exist. A console.log from a Cypress spec goes to the BROWSER console and never reaches the runner stdout, so redirecting the runner is not enough: write the number down.",
            "- Never transcribe the list of gates into tasks.md. Derive it from the repository every time - the scripts in package.json plus what the CI workflows actually run. A transcribed list inherits its predecessor's omissions: that is how the same green-but-incomplete gate was missed on three consecutive changes.",
            "- Report numbers with every measurement (\"1296 tests, 0 failures\"), plus the RED logs of defects you found. A green log alone proves nothing was found.",
            "- Announce any deviation from a design decision BEFORE implementing it, and say what you deliberately will NOT touch.",
            "- This worktree copy of the change is now authoritative; the one in the main checkout is stale.",
        )
    }
    override val recordSession =
        "In aria-meta.md, add a classification row `| **Spec session** | <this session name and [ref] from ListAgents, followed by \"historical trace, stale by construction - verify before use\"> |`. It is the fallback channel, not the main one: when you launch the implementation through a request, the plugin carries your name from that request straight into the implementer's prompt, and that address is fresh by construction. The row only reaches work someone starts by hand from the tool window. Never present it as an address to trust - the reader has to make a session NAME THIS CHANGE before relying on it, because a session name changes under a running session with its context untouched (measured, 2026-08-31: one carried three names in an hour) and the name it leaves behind gets handed to a session that never saw this work, which then answers politely."
    override val delegation = listOf(
        "",
        "Delegate reading, never deciding. Your own context is the scarce resource: when it runs out, correction round-trips cost the most, exactly when they are needed most.",
        "- Delegate to a subagent: taking inventory (call sites, implementations of an interface, who writes a given field), reading more than two files to answer ONE question, checking a long diff against the spec. It returns a conclusion and file:line references, never a dump of file contents.",
        "- Word a delegated review as a refutation, not a check: \"find what makes this wrong, and say what would have to be true for it to be right\" rather than \"verify this\". Measured on 2026-09-01/02, audits launched with that wording returned 15 surviving mutants under a green CI, plus a false premise about third-party code.",
        "- A uniform result across a mutation campaign is a symptom of the tooling, not a verdict on the tests. Measured, two campaigns out of four were invalidated that way - a `git checkout --` on an uncommitted target, and a cmd.exe that does not resolve mvnw.cmd. Before reporting an all-survived or an all-killed, prove the harness can produce the other answer: plant one mutant you know the tests catch, and one you know they do not.",
        "- Never delegate: the design decision, the escalation, the code of the task itself, and the final verification. Re-run the gates yourself - a subagent report says what it BELIEVES it did.",
        "- Redirect any bulky output to a file and read its tail: builds, test runs, long diffs. `cmd | tail` hides the exit code, so redirect first and filter second.",
        "- In an IDE, every Edit re-sends the whole edited file's diagnostics, not just those of your change. Group edits to one file into a single write rather than eight small ones. The reading you take AFTER the grouped write is your verification of it; eight readings of the same unchanged file are not eight verifications.",
    )
    override fun launchRequest(dir: String) = listOf(
        "",
        "Once I have validated the spec, you may launch the implementation yourself. Not with git — write a request, and I approve it.",
        "- One JSON file per worktree, in $dir, named after what it launches: {\"change\": \"<change-name>\", \"branch\": \"<branch>\", \"from\": \"<your own name from ListAgents>\", \"prompt\": \"<the whole prompt the implementer will receive>\", \"reason\": \"<one line: what this worktree owns>\"}.",
        "- Only \"change\" is required. Left out, the branch follows the configured pattern and the prompt is the one the tool window button would have produced.",
        "- The same file also hands a change already in flight to a fresh session — your own context saturating, a night run picked up in the morning. Write the same request, with the same \"change\": the plugin sees that the change lives on a branch with a worktree and opens a session there instead, creating no branch, no worktree, and moving nothing. There is no field to set and no other file to write; do not ask for a new change name to work around it, and do not commit a handover document by hand.",
        "- Add \"worktree\": \"<path or directory name>\" only when several worktrees carry that change and the plugin says so. It picks one; it never decides between resuming and creating.",
        "- Do not open your prompt with the worktree path, the branch or \"work in that worktree\": the plugin puts that frame above whatever you write, because a session is handed a prompt and never a working directory, and one that omits it starts in the main checkout. Write the task, not the place.",
        "- Write the prompt yourself whenever that default would be wrong. Being able to is the whole reason you are asking instead of me clicking — and name yourself in it, your [ref] as well as your name, so the implementer reports to you rather than to the repository at large. Tell it to make you name the change before it trusts that address: names get recycled, and the stranger who answers to yours later will answer politely.",
        "- Ask for several worktrees only when the parts can genuinely run in parallel. Say in each \"reason\" what that one owns and what it must not touch, and say which of them has to merge first.",
        "- I read every prompt in a tab, and I may edit it before launching or refuse outright. Nothing is created until I answer. The answer lands beside your request as <name>.result.json, and it tells you whether I changed the prompt and how.",
        "- Do not poll that file: the session I launch is told to message you before it writes a line.",
    )
    override fun handOver(dir: String) = listOf(
        "",
        "When your own context starts to saturate, hand this change over rather than compact yourself into a stranger. You can do it without me and without git.",
        "- Write one JSON file in $dir — the queue lives in the main checkout, not here: {\"change\": \"<this change>\", \"from\": \"<your name from ListAgents>\", \"prompt\": \"<everything the next session needs that the repository does not already say>\", \"reason\": \"<one line: why you are handing over>\"}.",
        "- Name nothing else. The plugin sees that this change lives on a branch that has a worktree, and opens the session in THIS worktree on THIS branch: no branch created, no worktree created, nothing moved. Do not invent a new change name to get a fresh one, and do not commit a handover document by hand.",
        "- I read that prompt in a tab and may edit it before approving, exactly as for a first launch. The answer lands beside your request as <name>.result.json.",
        "- Put in it what only you know: what you measured and what you did not, which gates you actually ran and their numbers, the decision you were about to take. The next session inherits the branch, never your reasoning. Commit first — it reads commits, not your context.",
    )
}

private object FR : Strings {
    override fun continueTitle(name: String) = "Reprends le change OpenSpec « $name »."
    override fun archiveTitle(name: String) = "Archive le change OpenSpec terminé « $name »."
    override val worktreeTitle = "Travaille sur la branche de ce worktree."
    override val worktree = "Worktree"
    override val branch = "Branche"
    override fun position(ahead: Int, behind: Int, base: String) = "Position : $ahead en avance / $behind en retard sur $base"
    override fun filesChanged(count: Int, base: String) = "Fichiers modifiés vs $base : $count"
    override val change = "Change OpenSpec"
    override fun tasks(done: Int, total: Int) = "Tâches : $done/$total faites"
    override val execMode = "Mode exec"
    override val remaining = "Tâches restantes :"
    override fun andMore(count: Int) = "- …et $count autres dans tasks.md"
    override val noChange = "Aucun change OpenSpec sur cette branche."
    override fun changesOnBranch(names: String) = "Changes OpenSpec sur cette branche : $names"
    override val stayInWorktree = "Travaille dans ce worktree, pas dans le checkout principal."
    override fun applyWithAria(skill: String) = "Lis d'abord tasks.md et aria-meta.md s'il existe, puis utilise le workflow $skill."
    override val applyPlain = "Lis d'abord tasks.md, puis reprends le change en cochant les tâches au fur et à mesure."
    override fun archiveWithAria(skill: String) = "Utilise ensuite le workflow $skill."
    override val archivePlain = "Déplace ensuite le dossier du change sous openspec/changes/archive/."
    override val allChecked = "Toutes les tâches sont cochées. Vérifie que le travail a bien atterri avant d'archiver."
    override fun readFirst(base: String) =
        "Commence par lire le diff par rapport à $base pour voir ce que la branche fait déjà, puis " +
            "dis-moi ce que tu constates avant de modifier quoi que ce soit."
    override val newWorkTitle = "Cadre un nouveau chantier sous forme de change OpenSpec."
    override val repository = "Dépôt"
    override val baseBranch = "Branche de base"
    override fun activeChanges(names: String) = "Changes actifs, n'en duplique aucun : $names"
    override val noActiveChange = "Aucun change actif dans ce dépôt pour le moment."
    override val interview =
        "Demande-moi mon besoin avant d'écrire quoi que ce soit — utilise AskUserQuestion, une " +
            "question à la fois, et continue tant que le périmètre, les contraintes et ce qui est hors " +
            "périmètre ne sont pas clairs. Ne suppose rien, et ne pars pas d'une hypothèse sur ce que je " +
            "voulais probablement dire."
    override fun proposeWithAria(skill: String) = "Lance ensuite le workflow $skill pour produire le change."
    override val proposePlain =
        "Crée ensuite le change avec `openspec new change \"<nom-en-kebab-case>\"` et écris ses " +
            "artefacts : proposal.md, design.md quand la forme n'est pas évidente, tasks.md."
    override val proposeBare =
        "Si la CLI openspec n'est pas disponible, crée openspec/changes/<nom-en-kebab-case>/ à la " +
            "main, avec un proposal.md et un tasks.md de tâches à cocher, sur le modèle des autres changes."
    override val stayOnMain =
        "Travaille dans le checkout principal et arrête-toi là : ne crée pas de branche, pas de " +
            "worktree, et ne commite pas. Le change non commité est justement ce qui le marque brouillon."
    override fun handOff(button: String) =
        "Quand le change est écrit, donne-moi son nom et arrête-toi. Je relis la proposal, puis le " +
            "bouton « $button » de la fenêtre DevFlow crée la branche, le worktree et le premier commit."
    override fun reportTo(who: String) =
        "Rends compte à $who, la session qui a demandé ce lancement. Fais-lui nommer ce change avant de te fier à cette adresse : les noms de session sont recyclés, et l'inconnue qui hérite de l'un d'eux répond poliment."
    override fun observability(change: String, worktree: String, branch: String, base: String, specSession: String?): List<String> {
        val identity = "change $change, worktree $worktree, branche $branch, base $base"
        val lecture =
            "« lis tel fichier de ce worktree et dis-moi si X » est ce qui a déclenché toutes les trouvailles réelles mesurées du 31/08 au 03/09/2026"
        val connue = listOf(
            "Avant d'écrire une seule ligne, écris à $specSession - la session qui a demandé ce lancement, donc celle qui a rédigé cette spec - pour qu'elle observe ce travail. Établis que c'est BIEN elle avant de t'appuyer dessus : demande-lui de nommer ce change, et attends la réponse. Les noms de session sont recyclés, si bien qu'une adresse peut aujourd'hui désigner une session qui n'a jamais vu cette spec - mesuré le 31/08/2026, et l'inconnue répond poliment, ce qui est précisément ce qui la rend dangereuse. Si c'est la mauvaise qui répond, dis-le et abandonne l'adresse ; ne va pas chercher un public de remplacement.",
            "- Donne-lui : $identity. Ajoute les commandes exactes des gates, où atterrissent les rapports de test, et ta base jetable le cas échéant.",
            "- Demande-lui des lectures, pas une surveillance : $lecture. Un Monitor sur les commits lui reste ouvert - sondage de ce worktree toutes les 60 s, avec un fichier d'état des hashes vus - et elle peut en armer un ; mais sur les deux journées où il l'a été, il a produit 0 signalement sur 178, alors que les pairs s'annonçaient déjà leurs commits par message. Il n'a rien ajouté à cette annonce, et il reste aveugle tant que rien n'est commité. Quoi qu'elle arme : PAS notify_when_idle, mesuré inopérant.",
        )
        val anonyme = listOf(
            "Aucune session n'est enregistrée comme observant ce travail : ce lancement ne portait pas de demandeur. Ne va pas en chercher un - mesuré les 01-02/09/2026, la consigne de diffuser aux sessions de ce dépôt a produit 4 messages byte-identiques et 7 clauses d'esquive, contre 4 et 5 les jours d'avant son existence : aucune amélioration. Laisse plutôt la trace là où un lecteur la trouvera :",
            "- Ouvre chaque message que tu envoies, et chaque réponse que tu donnes, par une ligne : $identity. Sur ces mêmes deux journées, le bruit d'adressage valait 3 messages sur 55, tous dans les trois premières minutes ; c'est une phrase d'ouverture qui le supprime, pas une recherche de la bonne adresse.",
            "- Quand une session te contacte, fais-lui nommer ce change avant de te fier à l'adresse, puis traite-la comme ton observatrice : donne-lui les commandes exactes des gates, où atterrissent les rapports de test, et ta base jetable le cas échéant. Demande-lui des lectures plutôt qu'une surveillance - $lecture.",
        )
        return listOf("") + (if (specSession != null) connue else anonyme) + listOf(
            "- L'accord d'un pair n'est pas une mesure. Mesuré le 03/09/2026 : deux sessions ont tenu un consensus faux pendant 2 h 25, et il a atteint le corps d'une PR ; ce qui l'a cassé est une question de l'utilisateur, pas le dispositif de relecture, et ce qui le réfutait était déjà dans le dépôt. Un accord veut dire « va chercher le chiffre », jamais « confirmé ».",
            "- Commite chaque section dès qu'elle est finie et mesurée. Un observateur ne voit que les commits : son silence signifie « rien de commité », jamais « tout va bien ».",
            "- TOUTE mesure s'écrit dans un fichier dont tu donnes le chemin, sinon elle n'existe pas. Un console.log depuis un spec Cypress part dans la console du NAVIGATEUR et ne remonte jamais au stdout du runner : rediriger le runner ne suffit pas, il faut écrire le chiffre.",
            "- Ne recopie JAMAIS la liste des gates dans tasks.md. Dérive-la du dépôt à chaque fois : les scripts de package.json plus ce que les workflows CI lancent réellement. Une liste transcrite hérite des oublis de la précédente — c'est ainsi que le même gate vert-mais-incomplet a été manqué sur trois changes consécutifs.",
            "- Donne les chiffres à chaque mesure (« 1296 tests, 0 échec »), et les logs ROUGES des défauts trouvés. Un log vert seul ne prouve rien.",
            "- Annonce toute déviation d'une décision de design AVANT de l'implémenter, et dis ce que tu ne toucheras délibérément PAS.",
            "- La copie du change dans ce worktree fait foi ; celle du checkout principal est périmée.",
        )
    }
    override val recordSession =
        "Dans aria-meta.md, ajoute une ligne de classification `| **Spec session** | <le nom de cette session et son [ref] via ListAgents, suivis de « trace historique, périmée par construction — vérifier avant usage »> |`. C'est le canal de repli, pas le principal : quand tu lances l'implémentation par une request, le plugin porte ton nom de cette request jusque dans le prompt de l'implémenteur, et cette adresse est fraîche par construction. La ligne ne sert qu'au travail démarré à la main depuis la fenêtre DevFlow. Ne la présente jamais comme une adresse à laquelle se fier : le lecteur doit faire NOMMER CE CHANGE à une session avant de s'appuyer sur elle, parce qu'un nom de session change sous une session en cours, contexte intact (mesuré le 31/08/2026 : l'une en a porté trois en une heure), et que le nom qu'elle laisse derrière est attribué à une session qui n'a jamais vu ce travail, laquelle répond alors poliment."
    override val delegation = listOf(
        "",
        "Délègue la lecture, jamais la décision. Ton contexte est la ressource rare : quand il sature, les allers-retours de correction coûtent le plus cher, exactement au moment où on en a le plus besoin.",
        "- À déléguer à un sous-agent : recenser (call sites, implémentations d'une interface, qui écrit tel champ), lire plus de deux fichiers pour répondre à UNE question, confronter un diff long à la spec. Il rend une conclusion et des références fichier:ligne, jamais un dump de fichiers.",
        "- Formule une relecture déléguée comme une réfutation, pas comme un contrôle : « trouve ce qui rend ceci faux, et dis ce qu'il faudrait pour que ce soit juste » plutôt que « vérifie ceci ». Mesuré les 01-02/09/2026, les audits lancés avec cette formulation ont rendu 15 mutants survivants sous une CI verte, plus une prémisse fausse sur du code tiers.",
        "- Un résultat uniforme sur une campagne de mutation est un symptôme d'outillage, pas un verdict sur les tests. Mesuré : deux campagnes sur quatre ont été invalidées ainsi - un `git checkout --` sur une cible non commitée, et un cmd.exe qui ne résout pas mvnw.cmd. Avant d'annoncer un « tous survivants » ou un « tous tués », prouve que le harnais peut produire l'autre réponse : plante un mutant que les tests attrapent à coup sûr, et un qu'ils laissent passer à coup sûr.",
        "- À ne jamais déléguer : la décision de design, l'escalade, le code de la tâche elle-même, et la vérification finale. Relance les gates toi-même — un compte rendu de sous-agent dit ce qu'il CROIT avoir fait.",
        "- Redirige toute sortie volumineuse vers un fichier et lis-en le tail : builds, runs de tests, diffs longs. `cmd | tail` masque le code de sortie : rediriger d'abord, filtrer ensuite.",
        "- Dans un IDE, chaque Edit réémet les diagnostics du fichier ENTIER, pas seulement ceux de ta modification. Groupe les modifications d'un même fichier en une seule écriture plutôt qu'en huit petites. Le relevé qui suit l'écriture groupée EST ta vérification ; huit relevés du même fichier inchangé ne sont pas huit vérifications.",
    )
    override fun launchRequest(dir: String) = listOf(
        "",
        "Une fois la spec validée par moi, tu peux lancer toi-même l'implémentation. Pas avec git — tu écris une demande, et je l'approuve.",
        "- Un fichier JSON par worktree, dans $dir, nommé d'après ce qu'il lance : {\"change\": \"<nom-du-change>\", \"branch\": \"<branche>\", \"from\": \"<ton propre nom via ListAgents>\", \"prompt\": \"<le prompt complet que recevra l'implémenteur>\", \"reason\": \"<une ligne : ce que ce worktree prend en charge>\"}.",
        "- Seul \"change\" est obligatoire. Omis, la branche suit le pattern configuré et le prompt est celui qu'aurait produit le bouton de la fenêtre DevFlow.",
        "- Le même fichier sert aussi à confier un change déjà en cours à une session neuve — ton propre contexte qui sature, un run de nuit repris au matin. Écris la même demande, avec le même \"change\" : le plugin voit que le change vit sur une branche qui a un worktree et ouvre une session dedans, sans créer de branche, sans créer de worktree, sans rien déplacer. Aucun champ à poser, aucun autre fichier à écrire ; ne contourne pas en inventant un nouveau nom de change, et ne commite pas un document de passation à la main.",
        "- N'ajoute \"worktree\": \"<chemin ou nom de dossier>\" que si plusieurs worktrees portent ce change et que le plugin te le dit. Ce champ départage ; il ne décide jamais entre reprendre et créer.",
        "- N'ouvre pas ton prompt par le chemin du worktree, la branche ou « travaille dans ce worktree » : le plugin place ce cadre au-dessus de ce que tu écris, parce qu'une session reçoit un prompt et jamais un répertoire de travail, et que celle qui l'omet démarre dans le checkout principal. Écris la tâche, pas le lieu.",
        "- Écris le prompt toi-même chaque fois que ce défaut serait inadapté. C'est toute la raison pour laquelle tu demandes au lieu que je clique — et nomme-toi dedans, avec ton [ref] autant que ton nom, pour que l'implémenteur te rende compte à toi plutôt qu'à tout le dépôt. Dis-lui de te faire nommer le change avant de se fier à cette adresse : les noms sont recyclés, et l'inconnue qui répondra plus tard au tien répondra poliment.",
        "- Ne demande plusieurs worktrees que si les parties peuvent réellement avancer en parallèle. Dis dans chaque \"reason\" ce que celui-là prend en charge et ce qu'il ne doit pas toucher, et dis lequel doit merger en premier.",
        "- Je lis chaque prompt dans un onglet, et je peux le modifier avant de lancer ou refuser. Rien n'est créé tant que je n'ai pas répondu. La réponse atterrit à côté de ta demande en <nom>.result.json, et elle te dit si j'ai modifié le prompt et comment.",
        "- Ne sonde pas ce fichier : la session que je lance a pour consigne de t'écrire avant d'écrire une ligne.",
    )
    override fun handOver(dir: String) = listOf(
        "",
        "Quand ton contexte commence à saturer, passe la main sur ce change plutôt que de te compacter en une inconnue. Tu peux le faire sans moi et sans git.",
        "- Écris un fichier JSON dans $dir — la file vit dans le checkout principal, pas ici : {\"change\": \"<ce change>\", \"from\": \"<ton nom via ListAgents>\", \"prompt\": \"<tout ce dont la session suivante a besoin et que le dépôt ne dit pas déjà>\", \"reason\": \"<une ligne : pourquoi tu passes la main>\"}.",
        "- Ne nomme rien d'autre. Le plugin voit que ce change vit sur une branche qui a un worktree, et ouvre la session dans CE worktree sur CETTE branche : aucune branche créée, aucun worktree créé, rien de déplacé. N'invente pas un nouveau nom de change pour en obtenir un neuf, et ne commite pas un document de passation à la main.",
        "- Je lis ce prompt dans un onglet et je peux le modifier avant d'approuver, exactement comme pour un premier lancement. La réponse atterrit à côté de ta demande en <nom>.result.json.",
        "- Mets-y ce que toi seule sais : ce que tu as mesuré et ce que tu n'as pas mesuré, quels gates tu as réellement lancés et leurs chiffres, la décision que tu étais sur le point de prendre. La session suivante hérite de la branche, jamais de ton raisonnement. Commite d'abord — elle lit des commits, pas ton contexte.",
    )
}

private fun strings(language: PromptLanguage): Strings = if (language == PromptLanguage.FR) FR else EN

/** At most this many remaining tasks are quoted; the prompt points at the file for the rest. */
private const val MAX_QUOTED_TASKS = 8

/** git reports forward slashes, the JDK reports backslashes; a prompt should not mix them. */
fun slashes(p: String): String = p.replace('\\', '/')

/** Task labels can run to a full paragraph; quoted whole they drown the prompt. */
private fun shorten(label: String, max: Int = 160): String =
    if (label.length <= max) label else label.substring(0, max - 1).trimEnd() + "…"

/** Every prompt opens with where the work lives; Claude cannot be handed a working directory. */
private fun worktreeLines(worktree: BoardWorktree, base: String, s: Strings): List<String> {
    val lines = mutableListOf("${s.worktree}: ${slashes(worktree.path)}", "${s.branch}: ${worktree.branch}")
    if (!worktree.isMain) {
        lines.add(s.position(worktree.ahead, worktree.behind, base))
        if (worktree.filesChanged > 0) lines.add(s.filesChanged(worktree.filesChanged, base))
    }
    return lines
}

private fun changeLines(change: AriaChange, s: Strings): List<String> =
    listOf("${s.change}: ${change.name} (${slashes(change.dir)})", s.tasks(change.done, change.total))

object Prompts {
    /**
     * The frame an agent-written prompt is not allowed to omit: where the work lives, and the
     * instruction to stay there. The agent supplies the task; the plugin supplies the where.
     */
    fun locationFrame(worktree: BoardWorktree, change: AriaChange, base: String, language: PromptLanguage): String {
        val s = strings(language)
        val lines = (worktreeLines(worktree, base, s) + changeLines(change, s)).toMutableList()
        if (change.specSession != null) {
            lines.add("")
            lines.add(s.reportTo(change.specSession))
        }
        return (lines + listOf("", s.stayInWorktree, "", "")).joinToString("\n")
    }

    /** Prompt to pick a change back up where it stopped. */
    fun continuePrompt(
        worktree: BoardWorktree,
        change: AriaChange,
        base: String,
        plugin: AriaPlugin,
        language: PromptLanguage,
        requestDir: String? = null,
    ): String {
        val s = strings(language)
        val remaining = change.sections
            .flatMap { section -> section.tasks.filter { !it.done }.map { section to it } }
            .take(MAX_QUOTED_TASKS)

        val lines = mutableListOf(s.continueTitle(change.name), "")
        lines.addAll(worktreeLines(worktree, base, s))
        lines.addAll(changeLines(change, s))
        if (change.execMode != null) lines.add("${s.execMode}: ${change.execMode}")
        lines.add("")
        lines.add(s.remaining)
        for ((section, task) in remaining) {
            lines.add("- [${section.title}] ${shorten(task.label)}")
        }
        val left = change.total - change.done
        if (left > remaining.size) lines.add(s.andMore(left - remaining.size))
        lines.addAll(s.observability(change.name, slashes(worktree.path), worktree.branch, base, change.specSession))
        lines.addAll(s.delegation)
        if (requestDir != null) lines.addAll(s.handOver(slashes(requestDir)))
        lines.add("")
        lines.add(s.stayInWorktree)
        lines.add(if (plugin.installed) s.applyWithAria("aria:opsx:apply") else s.applyPlain)
        return lines.joinToString("\n")
    }

    /** Prompt for a change whose tasks are all checked but which still sits outside archive/. */
    fun archivePrompt(worktree: BoardWorktree, change: AriaChange, base: String, plugin: AriaPlugin, language: PromptLanguage): String {
        val s = strings(language)
        return (
            listOf(s.archiveTitle(change.name), "") +
                worktreeLines(worktree, base, s) +
                changeLines(change, s) +
                listOf("", s.allChecked, if (plugin.installed) s.archiveWithAria("aria:opsx:archive") else s.archivePlain)
            ).joinToString("\n")
    }

    /** Prompt for a worktree with no OpenSpec change at all: a plain fix branch. */
    fun worktreePrompt(worktree: BoardWorktree, base: String, language: PromptLanguage): String {
        val s = strings(language)
        val lines = mutableListOf(s.worktreeTitle, "")
        lines.addAll(worktreeLines(worktree, base, s))
        lines.add(
            if (worktree.changes.isNotEmpty()) s.changesOnBranch(worktree.changes.joinToString(", ") { it.name }) else s.noChange,
        )
        lines.add("")
        lines.add(s.stayInWorktree)
        lines.add(s.readFirst(base))
        return lines.joinToString("\n")
    }

    /**
     * Prompt that opens a new piece of work: an interview first, then an OpenSpec change written
     * in the main checkout and left uncommitted. The tool window creates the worktree afterwards.
     */
    fun newWorkPrompt(
        repoRoot: String,
        base: String,
        activeChanges: List<String>,
        plugin: AriaPlugin,
        buttonLabel: String,
        language: PromptLanguage,
        requestDir: String? = null,
    ): String {
        val s = strings(language)
        val lines = mutableListOf(
            s.newWorkTitle,
            "",
            "${s.repository}: ${slashes(repoRoot)}",
            "${s.baseBranch}: $base",
            if (activeChanges.isNotEmpty()) s.activeChanges(activeChanges.joinToString(", ")) else s.noActiveChange,
            "",
            s.interview,
        )
        if (plugin.installed) {
            lines.add(s.proposeWithAria("aria:opsx:propose"))
        } else {
            lines.add(s.proposePlain)
            lines.add(s.proposeBare)
        }
        lines.add("")
        lines.add(s.recordSession)
        lines.add("")
        lines.add(s.stayOnMain)
        lines.add(s.handOff(buttonLabel))
        if (requestDir != null) lines.addAll(s.launchRequest(slashes(requestDir)))
        return lines.joinToString("\n")
    }
}
