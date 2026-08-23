package dev.ashwake.ui.character.render

/**
 * Спрайты персонажа.
 *
 * Файл собран `tools/sprites/generate.py` — руками его не правят: силуэты
 * заданы формой в генераторе, и свет там расставляется одним правилом на
 * все предметы. Поправить куртку значит поправить генератор и перегенерировать
 * всё, иначе освещение разъедется от вещи к вещи.
 *
 * Сетка «куклы» 32×56, шаг на холсте 128×128 — 2 пикселя. Спрайт обрезан
 * до содержимого, [x] и [y] — где он лежит на этой сетке.
 *
 * Символы строки: `.` пусто, `o` контур, `d` тень, `m` основной тон,
 * `l` свет, `w` блик, `k` кожа, `K` тень кожи, `e` глаз.
 */
object CharacterSprites {

    const val DOLL_WIDTH = 32
    const val DOLL_HEIGHT = 56

    /** Во сколько раз пиксель куклы больше пикселя холста 128×128. */
    const val DOLL_STEP = 2

    /** Голая фигура: рисуется всегда и под всем остальным. */
    val body: Sprite = Sprite(
        x = 5,
        y = 0,
        rows = listOf(
        "........oooooo........",
        ".......okkkkKKo.......",
        "......okkkkkKKKo......",
        ".....okkkkkkKKKKo.....",
        ".....okkkkkkKKKKo.....",
        ".....okkkkkkKKKKo.....",
        ".....okkkkkkKKKKo.....",
        ".....okkekkkKeKKo.....",
        ".....okkkkkkKKKKo.....",
        "......okkkkkKKKo......",
        "......okkkkkKKKo......",
        ".......okkkkKKo.......",
        "........okkKKo........",
        "........okkkKo........",
        ".....ooookkkKoooo.....",
        ".ooookkkkkkkkKKKKoooo.",
        "okkkKkkkkkkkkKKKKKKkko",
        "okkkKokkkkkkKKKKoKKkko",
        "okkkKokkkkkkKKKKoKKkko",
        "okkkKokkkkkkKKKKoKKkko",
        "okkkKokkkkkkKKKKoKKkko",
        "okkkKokkkkkkKKKKoKKkko",
        "okkkKokkkkkkKKKKoKKkko",
        "okkkKookkkkkKKKooKKkko",
        "okkkKookkkkkKKKooKKkko",
        "okkkKookkkkkKKKooKKkko",
        "okkkKookkkkkKKKooKKkko",
        "okkkKokkkkkkKKKKoKKkko",
        "okkkKokkkkkkKKKKoKKkko",
        ".okkKokkkkkkKKKKoKKko.",
        ".okkKokkkkkkKKKKoKKko.",
        "okkkKokkkkkkKKKKoKKkko",
        "okkkKokkkkkkKKKKoKKkko",
        "okkkKokkkkkkKKKKoKKkko",
        ".oooookkkkkkKKKKooooo.",
        "....okkkKKooKKKkko....",
        "....okkkKKooKKKkko....",
        "....okkkKKooKKKkko....",
        "....okkkKKooKKKkko....",
        "....okkkKKooKKKkko....",
        "....okkkKKooKKKkko....",
        "....okkkKKooKKKkko....",
        "....okkkKKooKKKkko....",
        "....okkkKKooKKKkko....",
        "....okkkKKooKKKkko....",
        "....okkkKKooKKKkko....",
        "....okkkKKooKKKkko....",
        "....okkkKKooKKKkko....",
        "....okkkKKooKKKkko....",
        "....okkkKKooKKKkko....",
        "....okkkKKooKKKkko....",
        "....okkkKKooKKKkko....",
        "....oKKKKKooKKKKKKo...",
        "....oKKKKKooKKKKKKo...",
        "....oKKKKKooKKKKKKo...",
        ".....ooooo..oooooo...."
        )
    )

    /** id предмета из каталога → спрайт. Нет в таблице — предмет не рисуется. */
    val items: Map<String, Sprite> = mapOf(
        "back_backpack" to Sprite(
            x = 8,
            y = 17,
            rows = listOf(
            ".oooooooooooooo.",
            "ollllmmmmmmddddo",
            "ollllmmmmmmddddo",
            "ollllmmmmmmddddo",
            "ollllmmmmmmddddo",
            ".olllmmmmmmdddo.",
            ".olllmmmmmmdddo.",
            ".owwwwwwwwwwwwo.",
            ".olllmmmmmmdddo.",
            ".olllmmmmmmdddo.",
            ".olllmmmmmmdddo.",
            ".olllmmmmmmdddo.",
            "ollllmmmmmmddddo",
            "ollllmmmmmmddddo",
            "ollllmmmmmmddddo",
            ".oooooooooooooo."
            )
        ),
        "back_quiver" to Sprite(
            x = 19,
            y = 13,
            rows = listOf(
            ".ooooo.",
            "owllllo",
            "owllllo",
            "owllllo",
            "owllllo",
            "odddddo",
            "odddddo",
            "odddddo",
            "odddddo",
            "odddddo",
            "odddddo",
            "odddddo",
            "odddddo",
            "odddddo",
            "odddddo",
            "odddddo",
            "odddddo",
            ".ooooo."
            )
        ),
        "back_yogamat" to Sprite(
            x = 5,
            y = 18,
            rows = listOf(
            ".ooooooooooooooooooo.",
            "olllllllllllllllllllo",
            "ommmmmmmmmmmmmmmmmmmo",
            "ommmmmmmmmmmmmmmmmmmo",
            "ommmmmmmmmmmmmmmmmmmo",
            ".ooooooooooooooooooo."
            )
        ),
        "belt_leather" to Sprite(
            x = 9,
            y = 32,
            rows = listOf(
            ".oooooooooooo.",
            "olllmmwmmmdddo",
            "olllmmwmmmdddo",
            ".oooooooooooo."
            )
        ),
        "belt_plate" to Sprite(
            x = 9,
            y = 31,
            rows = listOf(
            ".oooooooooooo.",
            "ollllmwmmddddo",
            "olllmmmmmmdddo",
            "olllmmmmmmdddo",
            "olllmmwmmmdddo",
            ".oooooooooooo."
            )
        ),
        "belt_pouches" to Sprite(
            x = 9,
            y = 32,
            rows = listOf(
            ".oooooooooooo.",
            "olllmmwmmmdddo",
            "olllmmwmmmdddo",
            "ommmoooooddddo",
            "ommmo...oddddo",
            "ommmo...oddddo",
            ".ooo.....oooo."
            )
        ),
        "boots_boots" to Sprite(
            x = 8,
            y = 45,
            rows = listOf(
            ".oooooo.ooooooo.",
            "ollllllodddddddo",
            "ollllllodddddddo",
            "ollllllodddddddo",
            "ollllllodddddddo",
            "ollllllodddddddo",
            "ollllllodddddddo",
            "ollllllodddddddo",
            ".oooooo.ooooooo."
            )
        ),
        "boots_sabatons" to Sprite(
            x = 8,
            y = 45,
            rows = listOf(
            ".oooooooooooooo.",
            "ollllllldddddddo",
            "ollllllldddddddo",
            "ollllllldddddddo",
            "ollllllldddddddo",
            "ollllllldddddddo",
            "ollllllldddddddo",
            "ollllllldddddddo",
            ".oooooooooooooo."
            )
        ),
        "boots_slippers" to Sprite(
            x = 9,
            y = 49,
            rows = listOf(
            ".oooooo.oooooo.",
            "olllllloddddddo",
            "olllllloddddddo",
            "olllllloddddddo",
            "olllllloddddddo",
            ".oooooo.oooooo."
            )
        ),
        "boots_sneakers" to Sprite(
            x = 9,
            y = 48,
            rows = listOf(
            ".oooooo.oooooo.",
            "olllllloddddddo",
            "olllllloddddddo",
            "olllllloddddddo",
            "olllllloddddddo",
            ".oooooo.oooooo."
            )
        ),
        "chest_cuirass" to Sprite(
            x = 5,
            y = 15,
            rows = listOf(
            "...oooooooooooooooo...",
            ".oolllllmmlmmmdddddoo.",
            "ollllollmmlmmmddoddddo",
            "olwwwwwwwwlwwwwwwwwwdo",
            "ollllollmmlmmmddoddddo",
            "ollllollmmlmmmddoddddo",
            ".ooollllmmlmmmddddooo.",
            "...owwwwwwlwwwwwwwo...",
            "....olllmmlmmmdddo....",
            "....olllmmlmmmdddo....",
            "....olllmmlmmmdddo....",
            "....owwwwwlwwwwwwo....",
            "...ollllmmlmmmddddo...",
            "...ollllmmlmmmddddo...",
            "...ollllmmlmmmddddo...",
            "...owwwwwwlwwwwwwwo...",
            "...ollllmmlmmmddddo...",
            "...ollllmmlmmmddddo...",
            "...ollllmmlmmmddddo...",
            "....oooooooooooooo...."
            )
        ),
        "chest_hoodie" to Sprite(
            x = 5,
            y = 12,
            rows = listOf(
            ".........oooo.........",
            "........olmmdo........",
            "........olmmdo........",
            "....ooooolmmdooooo....",
            ".ooollllmlmmdmddddooo.",
            "ollllollmmmmmmddoddddo",
            "ollllollmmmmmmddoddddo",
            "ollllollmmmmmmddoddddo",
            "ollllollmmmmmmddoddddo",
            "ollllollmmmmmmddoddddo",
            "ollllolllmmmmdddoddddo",
            "olllllollmmmmddodddddo",
            "olllllollmmmmddodddddo",
            "olllllollmmmmddodddddo",
            "olllllollmmmmddodddddo",
            "ollllollmmmmmmddoddddo",
            "ollllollmmmmmmddoddddo",
            "ollllollmmmmmmddoddddo",
            "ollllollmmmmmmddoddddo",
            "ollllollmmmmmmddoddddo",
            "ollllollmmmmmmddoddddo",
            ".ooollllmmmmmmddddooo.",
            "....oooooooooooooo...."
            )
        ),
        "chest_jacket" to Sprite(
            x = 5,
            y = 15,
            rows = listOf(
            "....oooo......oooo....",
            ".ooollllo....oddddooo.",
            "ollllollmo..omddoddddo",
            "ollllollmmoommddoddddo",
            "ollllollmmmmmmddoddddo",
            "ollllollmmmmmmddoddddo",
            "ollllollmmmmmmddoddddo",
            "ollllollmmmmmmddoddddo",
            "olllllollmmmmddodddddo",
            "olllllollmmmmddodddddo",
            "olllllollmmmmddodddddo",
            "olllllolmmmmmmdodddddo",
            "ollllollmmmmmmddoddddo",
            "ollllollmmmmmmddoddddo",
            "ollllollmmmmmmddoddddo",
            "ollllollmmmmmmddoddddo",
            "ollllollmmmmmmddoddddo",
            "ollllollmmmmmmddoddddo",
            ".ooollllmmmmmmddddooo.",
            "....oooooooooooooo...."
            )
        ),
        "chest_leather" to Sprite(
            x = 5,
            y = 15,
            rows = listOf(
            "....oooo......oooo....",
            ".ooollllo....oddddooo.",
            "ollllollmo..omddoddddo",
            "ollllollmmoommddoddddo",
            "ollllollmmmmmmddoddddo",
            "ollllollmmmmmmddoddddo",
            "ollllollmmmmmmddoddddo",
            "ollllolllmmmmdddoddddo",
            "olllllollmmmmddodddddo",
            "olllllollmmmmddodddddo",
            ".oooollllmmmmddddoooo.",
            "....ollllmmmmddddo....",
            "....olllmmmmmmdddo....",
            "....olllmmmmmmdddo....",
            "....olllmmmmmmdddo....",
            "....olllmmmmmmdddo....",
            "....olllmmmmmmdddo....",
            "....olllmmmmmmdddo....",
            ".....oooooooooooo....."
            )
        ),
        "chest_pyjama" to Sprite(
            x = 5,
            y = 15,
            rows = listOf(
            "....oooo......oooo....",
            ".ooollllooooooddddooo.",
            "ollllollmmmmmmddoddddo",
            "ollllollmmmmmmddoddddo",
            "ollllollmmmmmmddoddddo",
            "ollllollmmmmmmddoddddo",
            "ollllolllmmmmdddoddddo",
            "ollllolllmmmmdddoddddo",
            "olwwwwwwwwwwwwwwwwwwdo",
            "olllllollmmmmddodddddo",
            "olllllollmmmmddodddddo",
            "olllllollmmmmddodddddo",
            "olwwwwwwwwwwwwwwwwwwdo",
            "ollllolllmmmmdddoddddo",
            "ollllolllmmmmdddoddddo",
            "ollllollmmmmmmddoddddo",
            "ollllollmmmmmmddoddddo",
            ".oooolllmmmmmmdddoooo.",
            ".....oooooooooooo....."
            )
        ),
        "chest_robe" to Sprite(
            x = 4,
            y = 15,
            rows = listOf(
            "....ooooo......ooooo....",
            "..oolllllo....odddddoo..",
            ".ollllollmo..omddoddddo.",
            ".ollllollmmoommddoddddo.",
            ".ollllollmmmmmmddoddddo.",
            ".ollllollmmmmmmddoddddo.",
            ".ollllollmmmmmmddoddddo.",
            ".ollllollmmmmmmddoddddo.",
            ".olllllolmmmmmmdodddddo.",
            ".olllllolmmmmmmdodddddo.",
            ".olllllolmmmmmmdodddddo.",
            ".olllllolmmmmmmdodddddo.",
            ".ollllollmmmmmmddoddddo.",
            ".ollllollmmmmmmddoddddo.",
            ".ollllollmmmmmmddoddddo.",
            ".ollllollmmmmmmddoddddo.",
            "olllllollmmmmmmddodddddo",
            "olllllollmmmmmmddodddddo",
            "olllllollmmmmmmddodddddo",
            ".oooollllmmmmmmddddoooo.",
            "....ollllmmmmmmddddo....",
            "....ollllmmmmmmddddo....",
            "....ollllmmmmmmddddo....",
            "....ollllmmmmmmddddo....",
            "....ollllmmmmmmddddo....",
            "....ollllmmmmmmddddo....",
            "....ollllmmmmmmddddo....",
            "....ollllmmmmmmddddo....",
            "....ollllmmmmmmddddo....",
            "....ollllmmmmmmddddo....",
            "....ollllmmmmmmddddo....",
            "....ollllmmmmmmddddo....",
            "....ollllmmmmmmddddo....",
            "....ollllmmmmmmddddo....",
            "....ollllmmmmmmddddo....",
            "....ollllmmmmmmddddo....",
            ".....oooooooooooooo....."
            )
        ),
        "chest_shirt" to Sprite(
            x = 5,
            y = 15,
            rows = listOf(
            "....oooo......oooo....",
            ".ooollllo....oddddooo.",
            "ollllollmo..omddoddddo",
            "ollllollmmoommddoddddo",
            "ollllolllmmmmdddoddddo",
            "ollllolllmmmmdddoddddo",
            "ollllolllmmmmdddoddddo",
            "ollllolllmmmmdddoddddo",
            "olllloollmmmmddooddddo",
            "olllloollmmmmddooddddo",
            "olllloollmmmmddooddddo",
            "olllloollmmmmddooddddo",
            "ollllolllmmmmdddoddddo",
            "ollllolllmmmmdddoddddo",
            "ollllolllmmmmdddoddddo",
            "ollllolllmmmmdddoddddo",
            "ollllolllmmmmdddoddddo",
            "ollllooooooooooooddddo",
            ".oooo............oooo."
            )
        ),
        "chest_sportwear" to Sprite(
            x = 5,
            y = 15,
            rows = listOf(
            ".....ooo......ooo.....",
            ".oooollloooooodddoooo.",
            "ollllollmmmmmmddoddddo",
            "ollllollmmmmmmddoddddo",
            "ollllolllmmmmdddoddddo",
            "olwwwwwwwwwwwwwwwwwwdo",
            "olwwwwwwwwwwwwwwwwwwdo",
            ".ooooolllmmmmdddooooo.",
            ".....olllmmmmdddo.....",
            ".....olllmmmmdddo.....",
            ".....olllmmmmdddo.....",
            ".....olllmmmmdddo.....",
            ".....olllmmmmdddo.....",
            "....ollllmmmmddddo....",
            "....ollllmmmmddddo....",
            ".....oooooooooooo....."
            )
        ),
        "chest_sweater" to Sprite(
            x = 5,
            y = 15,
            rows = listOf(
            "....oooo......oooo....",
            ".ooollllooooooddddooo.",
            "ollllollmmmmmmddoddddo",
            "ollllollmmmmmmddoddddo",
            "ollllollmmmmmmddoddddo",
            "ollllollmmmmmmddoddddo",
            "ollllollmmmmmmddoddddo",
            "ollllolllmmmmdddoddddo",
            "olllllollmmmmddodddddo",
            "olllllollmmmmddodddddo",
            "olllllollmmmmddodddddo",
            "olllllollmmmmddodddddo",
            "ollllolllmmmmdddoddddo",
            "ollllolllmmmmdddoddddo",
            "ollllollmmmmmmddoddddo",
            "ollllollmmmmmmddoddddo",
            "ollllollmmmmmmddoddddo",
            "ollllollmmmmmmddoddddo",
            ".oooo.oooooooooo.oooo."
            )
        ),
        "chest_tshirt" to Sprite(
            x = 5,
            y = 15,
            rows = listOf(
            ".....ooo......ooo.....",
            ".oooollloooooodddoooo.",
            "ollllollmmmmmmddoddddo",
            "ollllollmmmmmmddoddddo",
            "ollllolllmmmmdddoddddo",
            "ollllolllmmmmdddoddddo",
            "ollllolllmmmmdddoddddo",
            "ollllolllmmmmdddoddddo",
            ".ooooolllmmmmdddooooo.",
            ".....olllmmmmdddo.....",
            ".....olllmmmmdddo.....",
            ".....olllmmmmdddo.....",
            ".....olllmmmmdddo.....",
            "....ollllmmmmddddo....",
            "....ollllmmmmddddo....",
            "....ollllmmmmddddo....",
            ".....oooooooooooo....."
            )
        ),
        "cloak_long" to Sprite(
            x = 4,
            y = 14,
            rows = listOf(
            "....ooo..........ooo....",
            "...olllo........odddo...",
            "...olllo........odddo...",
            "...ollllo......oddddo...",
            "...ollllo......oddddo...",
            "...ollllo......oddddo...",
            "...ollllo......oddddo...",
            "...ollllo......oddddo...",
            "....olllo......odddo....",
            "....ollllo....oddddo....",
            "....ollllo....oddddo....",
            "...olllllo....odddddo...",
            "...olllllo....odddddo...",
            "...ollllo......oddddo...",
            "...ollllo......oddddo...",
            "...ollllo......oddddo...",
            "...ollllo......oddddo...",
            "..olllllo......odddddo..",
            "..olllllo......odddddo..",
            "..olllllo......odddddo..",
            "..olllllo......odddddo..",
            "..olllllo......odddddo..",
            "..olllllo......odddddo..",
            "..olllllo......odddddo..",
            ".ollllllo......oddddddo.",
            ".ollllllo......oddddddo.",
            ".ollllllo......oddddddo.",
            ".ollllllo......oddddddo.",
            ".olllllo........odddddo.",
            ".olllllo........odddddo.",
            ".olllllo........odddddo.",
            ".olllllo........odddddo.",
            ".olllllo........odddddo.",
            ".olllllo........odddddo.",
            "ollllllo........oddddddo",
            "ollllllo........oddddddo",
            "ollllllo........oddddddo",
            ".oooooo..........oooooo."
            )
        ),
        "cloak_scarf" to Sprite(
            x = 8,
            y = 14,
            rows = listOf(
            ".oooooooooooooo.",
            "ollllmmmmmmddddo",
            "ollllmmmmmmddddo",
            "ollllmmmmmmddddo",
            "ollllmmmmmmddddo",
            "ollllmmmmmmddddo",
            ".ooooooodddoooo.",
            ".......odddo....",
            ".......odddo....",
            ".......odddo....",
            ".......odddo....",
            ".......odddo....",
            ".......odddo....",
            "........ooo....."
            )
        ),
        "cloak_short" to Sprite(
            x = 5,
            y = 14,
            rows = listOf(
            "...ooo..........ooo...",
            "..olllo........odddo..",
            "..olllo........odddo..",
            "..ollllo......oddddo..",
            "..ollllo......oddddo..",
            "..ollllo......oddddo..",
            "..ollllo......oddddo..",
            "..ollllo......oddddo..",
            "..ollllo......oddddo..",
            "..olllllo....odddddo..",
            "..olllllo....odddddo..",
            "..olllllo....odddddo..",
            "..olllllo....odddddo..",
            "..ollllo......oddddo..",
            "..ollllo......oddddo..",
            ".olllllo......odddddo.",
            ".olllllo......odddddo.",
            ".olllllo......odddddo.",
            ".olllllo......odddddo.",
            ".olllllo......odddddo.",
            "ollllllo......oddddddo",
            ".oooooo........oooooo."
            )
        ),
        "face_beard" to Sprite(
            x = 12,
            y = 7,
            rows = listOf(
            ".o....o.",
            "oeo..oeo",
            ".oooooo.",
            "ollmmddo",
            ".olmmdo.",
            "..oldo..",
            "...oo..."
            )
        ),
        "face_clean" to Sprite(
            x = 13,
            y = 8,
            rows = listOf(
            "e....e"
            )
        ),
        "face_glasses" to Sprite(
            x = 12,
            y = 8,
            rows = listOf(
            "wewoowew"
            )
        ),
        "fx_runes" to Sprite(
            x = 4,
            y = 14,
            rows = listOf(
            "...........l.............",
            "...........d.............",
            ".........................",
            ".........................",
            ".........................",
            "...l................l....",
            "...d................d....",
            ".........................",
            ".........................",
            ".........................",
            ".........................",
            ".........................",
            ".........................",
            ".........................",
            ".........................",
            ".........................",
            "l.......................l",
            "d.......................d",
            ".........................",
            ".........................",
            ".........................",
            ".........................",
            ".........................",
            ".........................",
            ".........................",
            ".........................",
            ".........................",
            "...l................l....",
            "...d................d....",
            ".........................",
            ".........................",
            ".........................",
            "............l............",
            "............d............"
            )
        ),
        "fx_sparks" to Sprite(
            x = 5,
            y = 19,
            rows = listOf(
            ".l.....................",
            ".w.....................",
            ".......................",
            ".......................",
            "....................l..",
            "....................w..",
            ".......................",
            ".......................",
            "l......................",
            "w......................",
            "......................l",
            "......................w",
            ".......................",
            ".......................",
            "...l...................",
            "...w...................",
            ".......................",
            ".......................",
            "...................l...",
            "...................w..."
            )
        ),
        "gloves_bracers" to Sprite(
            x = 4,
            y = 26,
            rows = listOf(
            "..ooooo..........ooooo..",
            ".olllllo........odddddo.",
            ".olllllo........odddddo.",
            ".ollllo..........oddddo.",
            ".ollllo..........oddddo.",
            "ollllllo........oddddddo",
            "ollllllo........oddddddo",
            "ollllllo........oddddddo",
            "ollllllo........oddddddo",
            ".oooooo..........oooooo."
            )
        ),
        "gloves_cloth" to Sprite(
            x = 5,
            y = 30,
            rows = listOf(
            ".ooooo..........ooooo.",
            "olllllo........odddddo",
            "olllllo........odddddo",
            "olllllo........odddddo",
            "olllllo........odddddo",
            ".ooooo..........ooooo."
            )
        ),
        "gloves_leather" to Sprite(
            x = 5,
            y = 29,
            rows = listOf(
            ".oooo............oooo.",
            "ollllo..........oddddo",
            "olllllo........odddddo",
            "olllllo........odddddo",
            "olllllo........odddddo",
            "olllllo........odddddo",
            ".ooooo..........ooooo."
            )
        ),
        "hair_bun" to Sprite(
            x = 9,
            y = 0,
            rows = listOf(
            "...oommmmoo...",
            "..ollmmmmddo..",
            ".olllmmmmdddo.",
            ".olllmmmmdddo.",
            "ollooooooooddo",
            "ollo......oddo",
            "ollo......oddo",
            "ollo......oddo",
            ".oo........oo."
            )
        ),
        "hair_long" to Sprite(
            x = 9,
            y = 0,
            rows = listOf(
            "...oooooooo...",
            "..ollmmmmddo..",
            ".olllmmmmdddo.",
            ".olllmmmmdddo.",
            "ollooooooooddo",
            "ollo......oddo",
            "ollo......oddo",
            "ollo......oddo",
            "olllo....odddo",
            "olllo....odddo",
            ".olllo..odddo.",
            "..ollloodddo..",
            "...ollldddo...",
            "....oldddo....",
            "....oldddo....",
            "....oldddo....",
            "....oldddo....",
            ".....oooo....."
            )
        ),
        "hair_shaved" to Sprite(
            x = 9,
            y = 0,
            rows = listOf(
            "...oooooooo...",
            "..olllmmdddo..",
            "..ollmmmmddo..",
            ".olllmmmmdddo.",
            "ollllmmmmddddo",
            ".ooolmmmmdooo.",
            "...olmmmmdo...",
            "...olmmmmdo...",
            "....oooooo...."
            )
        ),
        "hair_short" to Sprite(
            x = 9,
            y = 0,
            rows = listOf(
            "...oooooooo...",
            "..ollmmmmddo..",
            ".olllmmmmdddo.",
            ".olllmmmmdddo.",
            "ollooooooooddo",
            "ollo......oddo",
            "ollo......oddo",
            "ollo......oddo",
            ".oo........oo."
            )
        ),
        "hair_tail" to Sprite(
            x = 9,
            y = 0,
            rows = listOf(
            "...oooooooo...",
            "..ollmmmmddo..",
            ".olllmmmmdddo.",
            ".olllmmmmdddo.",
            "ollooooooooddo",
            "ollo......oddo",
            "ollo......oddo",
            "ollo......oddo",
            ".oo.......oddo",
            "..........oddo",
            "..........oddo",
            "..........oddo",
            "..........oddo",
            "..........oddo",
            "..........oddo",
            "...........oo."
            )
        ),
        "helm_beanie" to Sprite(
            x = 9,
            y = 0,
            rows = listOf(
            "...oooooooo...",
            "..ollmmmmddo..",
            ".olllmmmmdddo.",
            "ollllmmmmddddo",
            "olllmmmmmmdddo",
            "olllmmmmmmdddo",
            "olllmmmmmmdddo",
            ".oooooooooooo."
            )
        ),
        "helm_cap" to Sprite(
            x = 7,
            y = 0,
            rows = listOf(
            ".....oooooooo.....",
            "....ollmmmmddo....",
            "...olllmmmmdddo...",
            "..ollllmmmmddddo..",
            "..olllmmmmmmdddo..",
            ".oolllmmmmmmdddoo.",
            "oddddddddddddddddo",
            ".oooooooooooooooo."
            )
        ),
        "helm_crown" to Sprite(
            x = 9,
            y = 0,
            rows = listOf(
            ".owowowowowo..",
            ".olololololo..",
            ".olllmmmmdddo.",
            "ollllmmmmddddo",
            "olllmmmmmmdddo",
            ".oooooooooooo."
            )
        ),
        "helm_hood" to Sprite(
            x = 8,
            y = 0,
            rows = listOf(
            "..olllmmmmdddo..",
            "..olllmmmmdddo..",
            ".ollllmmmmddddo.",
            ".olllmmmmmmdddo.",
            "ollllooooooddddo",
            "olllo......odddo",
            "olllo......odddo",
            "ollllo....oddddo",
            "ollllo....oddddo",
            ".olllo....odddo.",
            "..ollooooooddo..",
            "..olllmmmmdddo..",
            "..ollllldddddo..",
            "..ollllldddddo..",
            "..ollllldddddo..",
            "..ollllldddddo..",
            "..ollllldddddo..",
            "..ollllldddddo..",
            "...oooooooooo..."
            )
        ),
        "helm_knight" to Sprite(
            x = 8,
            y = 0,
            rows = listOf(
            "...oooooooooo...",
            "..olllmmmmdddo..",
            "..olllmmmmdddo..",
            ".ollllmmmmddddo.",
            ".olllmmmmmmdddo.",
            "ollllmmmmmmddddo",
            "ollllmmmmmmddddo",
            "ollloooooooodddo",
            ".ollldddddddddo.",
            "..olllmmmmdddo..",
            "..olllmmmmdddo..",
            "...olllmmdddo...",
            "....ollmmddo....",
            "....ollmmddo....",
            "....ollmmddo....",
            ".....oooooo....."
            )
        ),
        "legs_greaves" to Sprite(
            x = 8,
            y = 33,
            rows = listOf(
            ".oooooooooooooo.",
            "olllmmdmmmmddddo",
            "olllmmdmmmmddddo",
            "owwwwwwwwwwwwwwo",
            "olllmmdmmmmddddo",
            "olllmmdmmmmddddo",
            "olllmmdmmmmddddo",
            "owwwwwwwwwwwwwwo",
            "olllmmdmmmmddddo",
            "olllmmdmmmmddddo",
            "olllmmdmmmmddddo",
            "owwwwwwwwwwwwwwo",
            "olllmmdmmmmddddo",
            "olllmmdmmmmddddo",
            "olllmmdmmmmddddo",
            "owwwwwwwwwwwwwwo",
            "olllmmdmmmmddddo",
            "olllmmdmmmmddddo",
            "olllmmdmmmmddddo",
            ".oooooooooooooo."
            )
        ),
        "legs_jeans" to Sprite(
            x = 9,
            y = 33,
            rows = listOf(
            ".oooooooooooo.",
            "ollmmddmmmdddo",
            "ollmmddmmmdddo",
            "ollmmddmmmdddo",
            "ollmmddmmmdddo",
            "ollmmddmmmdddo",
            "ollmmddmmmdddo",
            "ollmmddmmmdddo",
            "ollmmddmmmdddo",
            "ollmmddmmmdddo",
            "ollmmddmmmdddo",
            "ollmmddmmmdddo",
            "ollmmddmmmdddo",
            "ollmmddmmmdddo",
            "ollmmddmmmdddo",
            "ollmmddmmmdddo",
            "ollmmddmmmdddo",
            "ollmmddmmmdddo",
            "ollmmddmmmdddo",
            ".oooooooooooo."
            )
        ),
        "legs_pyjama" to Sprite(
            x = 9,
            y = 33,
            rows = listOf(
            ".oooooooooooo.",
            "ollmmdmmmmdddo",
            "ollmmdmmmmdddo",
            "ollmmdmmmmdddo",
            "ollmmdmmmmdddo",
            "ollmmdmmmmdddo",
            "ollmmdmmmmdddo",
            "ollmmdmmmmdddo",
            "ollmmdmmmmdddo",
            "ollmmdmmmmdddo",
            "ollmmdmmmmdddo",
            "ollmmdmmmmdddo",
            "owwwwwwwwwwwwo",
            "ollmmdmmmmdddo",
            "ollmmdmmmmdddo",
            "ollmmdmmmmdddo",
            "ollmmdmmmmdddo",
            "ollmmdmmmmdddo",
            "ollmmdmmmmdddo",
            ".oooooooooooo."
            )
        ),
        "legs_shorts" to Sprite(
            x = 9,
            y = 33,
            rows = listOf(
            ".oooooooooooo.",
            "ollmmdmmmmdddo",
            "ollmmdmmmmdddo",
            "ollmmdmmmmdddo",
            "ollmmdmmmmdddo",
            "ollmmdmmmmdddo",
            "ollmmdmmmmdddo",
            "ollmmdmmmmdddo",
            "ollmmdmmmmdddo",
            "ollmmdmmmmdddo",
            ".oooooooooooo."
            )
        ),
        "legs_sweatpants" to Sprite(
            x = 9,
            y = 33,
            rows = listOf(
            ".ooooooooooooo.",
            "ollmmddmmmddddo",
            "ollmmddmmmddddo",
            "ollmmddmmmddddo",
            "ollmmddmmmddddo",
            "ollmmddmmmddddo",
            "ollmmddmmmddddo",
            "owwwwwwwwwwwwwo",
            "ollmmddmmmddddo",
            "ollmmddmmmddddo",
            "ollmmddmmmddddo",
            "ollmmddmmmddddo",
            "ollmmddmmmddddo",
            "ollmmddmmmddddo",
            "ollmmddmmmddddo",
            "ollmmddmmmddddo",
            "ollmmddmmmddddo",
            "ollmmddmmmddddo",
            "ollmmddmmmddddo",
            ".ooooooooooooo."
            )
        ),
        "main_book" to Sprite(
            x = 21,
            y = 27,
            rows = listOf(
            ".ooooo.",
            "ommwddo",
            "ommwddo",
            "ommwddo",
            "ommwddo",
            "ommwddo",
            "ommwddo",
            "ommwddo",
            ".ooooo."
            )
        ),
        "main_dumbbell" to Sprite(
            x = 22,
            y = 27,
            rows = listOf(
            ".oo.oo.",
            "oddoddo",
            "oddoddo",
            "oddmddo",
            "oddmddo",
            "oddmddo",
            "oddoddo",
            "oddoddo",
            ".oo.oo."
            )
        ),
        "main_mug" to Sprite(
            x = 20,
            y = 28,
            rows = listOf(
            ".oooooo..",
            "owwwwwwo.",
            "ommmmmmmo",
            "ommmmmmmo",
            "ommmmmmo.",
            "ommmmmmo.",
            ".oooooo.."
            )
        ),
        "main_staff" to Sprite(
            x = 22,
            y = 8,
            rows = listOf(
            ".oooo.",
            "owwwwo",
            "owwwwo",
            "owwwwo",
            "owwwwo",
            ".ommo.",
            ".ommo.",
            ".ommo.",
            ".ommo.",
            ".ommo.",
            ".ommo.",
            ".ommo.",
            ".ommo.",
            ".ommo.",
            ".ommo.",
            ".ommo.",
            ".ommo.",
            ".ommo.",
            ".ommo.",
            ".ommo.",
            ".ommo.",
            ".ommo.",
            ".ommo.",
            ".ommo.",
            ".ommo.",
            ".ommo.",
            ".ommo.",
            ".ommo.",
            ".ommo.",
            ".ommo.",
            ".ommo.",
            ".ommo.",
            "..oo.."
            )
        ),
        "main_sword" to Sprite(
            x = 22,
            y = 13,
            rows = listOf(
            "..oo..",
            ".owlo.",
            ".owlo.",
            ".owlo.",
            ".owlo.",
            ".owlo.",
            ".owlo.",
            ".owlo.",
            ".owlo.",
            ".owlo.",
            ".owlo.",
            ".owlo.",
            ".owlo.",
            ".owlo.",
            ".owlo.",
            ".owlo.",
            ".owlo.",
            ".owlo.",
            ".owlo.",
            ".owlo.",
            "oddddo",
            ".oddo.",
            ".oddo.",
            ".oddo.",
            "..oo.."
            )
        ),
        "main_torch" to Sprite(
            x = 21,
            y = 18,
            rows = listOf(
            "..oooo..",
            ".owwwwo.",
            ".owwwwo.",
            "owwwwwwo",
            ".ollllo.",
            ".ollllo.",
            "..oddo..",
            "..oddo..",
            "..oddo..",
            "..oddo..",
            "..oddo..",
            "..oddo..",
            "..oddo..",
            "..oddo..",
            "..oddo..",
            "..oddo..",
            "..oddo..",
            "...oo..."
            )
        ),
        "off_cat" to Sprite(
            x = 5,
            y = 25,
            rows = listOf(
            ".....oo.",
            "....oddo",
            "....oddo",
            ".ooooddo",
            "ommmmddo",
            "ommmmddo",
            "ommmmmo.",
            "ommmmmo.",
            "ommmmmo.",
            "ommmmmo.",
            ".ooooo.."
            )
        ),
        "off_lantern" to Sprite(
            x = 5,
            y = 26,
            rows = listOf(
            ".ooo..",
            "odddo.",
            "ommmmo",
            "omwwmo",
            "omwwmo",
            "omwwmo",
            "omwwmo",
            "ommmmo",
            ".oooo."
            )
        ),
        "off_shield" to Sprite(
            x = 2,
            y = 20,
            rows = listOf(
            "...ooooo..",
            "..ollmddo.",
            "..olmmddo.",
            ".ollmmmddo",
            ".ollmmmddo",
            ".ollmmmddo",
            "olllmmmddo",
            "olllmmmddo",
            "owwwwwwwwo",
            ".ollmmmddo",
            ".ollmmmddo",
            ".ollmmmddo",
            "..olmmddo.",
            "..ollmddo.",
            "...ooooo.."
            )
        ),
        "shoulders_fur" to Sprite(
            x = 4,
            y = 13,
            rows = listOf(
            "....o.o.o.o.o.o.o.o.....",
            ".ooomomomomomomomomoooo.",
            "ollmmmdmdmomomommmmmdddo",
            "ollmmmmdmomomomommmddddo",
            ".ollmmdmomomomomommmddo.",
            "..oooooo.o.o.o.o.ooooo.."
            )
        ),
        "shoulders_leather" to Sprite(
            x = 4,
            y = 14,
            rows = listOf(
            ".ooooooo........ooooooo.",
            "ollmmmddo......ommmddddo",
            "ollmmmddo......ommmddddo",
            ".oolmddo........ommddoo.",
            "...oooo..........oooo..."
            )
        ),
        "shoulders_plate" to Sprite(
            x = 3,
            y = 14,
            rows = listOf(
            ".ooooooooo......ooooooooo.",
            "olllmmmdddo....ommmmdddddo",
            "olllmmmdddo....ommmmdddddo",
            "olllmmmdddo....ommmmdddddo",
            ".ollmmmddo......ommmddddo.",
            "..ollmmdo........ommdddo..",
            "...ooooo..........ooooo..."
            )
        ),
        "under_gambeson" to Sprite(
            x = 5,
            y = 15,
            rows = listOf(
            "....oooo......oooo....",
            ".ooollllooooooddddooo.",
            "ollllollmmmmmmddoddddo",
            "oddddddddddddddddddddo",
            "ollllollmmmmmmddoddddo",
            "ollllollmmmmmmddoddddo",
            "oddddddddddddddddddddo",
            "ollllolllmmmmdddoddddo",
            "olllllollmmmmddodddddo",
            "oddddddddddddddddddddo",
            "olllllollmmmmddodddddo",
            "olllllollmmmmddodddddo",
            "oddddddddddddddddddddo",
            "ollllolllmmmmdddoddddo",
            "ollllolllmmmmdddoddddo",
            "oddddddddddddddddddddo",
            "ollllollmmmmmmddoddddo",
            ".oooo.oooooooooo.oooo."
            )
        ),
        "under_shirt" to Sprite(
            x = 5,
            y = 15,
            rows = listOf(
            ".....ooo......ooo.....",
            ".oooollloooooodddoooo.",
            "ollllollmmmmmmddoddddo",
            "ollllollmmmmmmddoddddo",
            "ollllolllmmmmdddoddddo",
            "ollllolllmmmmdddoddddo",
            "ollllolllmmmmdddoddddo",
            "ollllolllmmmmdddoddddo",
            "olllloollmmmmddooddddo",
            "olllloollmmmmddooddddo",
            ".ollloollmmmmddoodddo.",
            ".ollloollmmmmddoodddo.",
            ".olllolllmmmmdddodddo.",
            ".olllolllmmmmdddodddo.",
            ".olllolllmmmmdddodddo.",
            ".olllolllmmmmdddodddo.",
            "..ooo.oooooooooo.ooo.."
            )
        )
    )

    /** Спрайт: строки символов и место на сетке куклы. */
    data class Sprite(val x: Int, val y: Int, val rows: List<String>)
}
