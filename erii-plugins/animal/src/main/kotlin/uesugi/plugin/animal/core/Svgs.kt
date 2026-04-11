package uesugi.plugin.animal.core

import uesugi.plugin.animal.Animal

private fun loadResource(path: String): String = Animal::class.java.classLoader
    .getResourceAsStream(path)!!
    .bufferedReader()
    .readText()

val whiteFieldSvg: String = loadResource("persona/field/white-field.svg")

val snowyFieldSvg: String = loadResource("persona/field/snowy-field.svg")

val carrotAndCoinSvg: String = loadResource("persona/field/carrot-and-coin.svg")

val halloweenFieldSvg: String = loadResource("persona/field/halloween-field.svg")

val grassFieldSvg: String = loadResource("persona/field/grass-field.svg")

val snowHouseFieldSvg: String = loadResource("persona/field/snow-house-field.svg")

val snowHouseBackgroundSvg: String = loadResource("persona/field/snow-house-background.svg")

val snowGrassFieldSvg: String = loadResource("persona/field/snow-grass-field.svg")

val snowGrassBackgroundSvg: String = loadResource("persona/field/snow-grass-background.svg")

val grassChristmasTreeFieldSvg: String =
    loadResource("persona/field/grass-christmastree-field.svg")

val grassChristmasTreeBackgroundSvg: String =
    loadResource("persona/field/grass-christmastree-background.svg")

val dummyGuildFieldSvg: String = loadResource("persona/field/white-field.svg")

val logoShowingFieldSvg: String = loadResource("persona/field/logo-showing.svg")

val redSofaFieldSvg: String = loadResource("persona/field/red-sofa.svg")

val redComputerFieldSvg: String = loadResource("persona/field/red-computer.svg")

val folderFieldSvg: String = loadResource("persona/field/folder.svg")

val brickFieldSvg: String = loadResource("persona/field/brick.svg")

val brickChristmasFieldSvg: String = loadResource("persona/field/brick-christmas.svg")

val gooseSvg: String = loadResource("persona/animal/goose.svg")

val gooseSunglassesSvg: String = loadResource("persona/animal/goose-sunglasses.svg")

val gooseKotlinSvg: String = loadResource("persona/animal/goose-kotlin.svg")

val gooseJavaSvg: String = loadResource("persona/animal/goose-java.svg")

val gooseJsSvg: String = loadResource("persona/animal/goose-js.svg")

val gooseNodeSvg: String = loadResource("persona/animal/goose-node.svg")

val gooseSwiftSvg: String = loadResource("persona/animal/goose-swift.svg")

val gooseLinuxSvg: String = loadResource("persona/animal/goose-linux.svg")

val gooseSpringSvg: String = loadResource("persona/animal/goose-spring.svg")

val littleChickSvg: String = loadResource("persona/animal/little-chick.svg")

val littleChickSunglassesSvg: String =
    loadResource("persona/animal/little-chick-sunglasses.svg")

val littleChickKotlinSvg: String = loadResource("persona/animal/little-chick-kotlin.svg")

val littleChickJavaSvg: String = loadResource("persona/animal/little-chick-java.svg")

val littleChickJsSvg: String = loadResource("persona/animal/little-chick-js.svg")

val littleChickNodeSvg: String = loadResource("persona/animal/little-chick-node.svg")

val littleChickSwiftSvg: String = loadResource("persona/animal/little-chick-swift.svg")

val littleChickLinuxSvg: String = loadResource("persona/animal/little-chick-linux.svg")

val littleChickSpringSvg: String = loadResource("persona/animal/little-chick-spring.svg")

val penguinSvg: String = loadResource("persona/animal/penguin.svg")

val penguinSunglassesSvg: String = loadResource("persona/animal/penguin-sunglasses.svg")

val penguinKotlinSvg: String = loadResource("persona/animal/penguin-kotlin.svg")

val penguinJavaSvg: String = loadResource("persona/animal/penguin-java.svg")

val penguinJsSvg: String = loadResource("persona/animal/penguin-js.svg")

val penguinNodeSvg: String = loadResource("persona/animal/penguin-node.svg")

val penguinSwiftSvg: String = loadResource("persona/animal/penguin-swift.svg")

val penguinLinuxSvg: String = loadResource("persona/animal/penguin-linux.svg")

val penguinSpringSvg: String = loadResource("persona/animal/penguin-spring.svg")

val pigSvg: String = loadResource("persona/animal/pig.svg")

val pigSunglassesSvg: String = loadResource("persona/animal/pig-sunglasses.svg")

val pigKotlinSvg: String = loadResource("persona/animal/pig-kotlin.svg")

val pigJavaSvg: String = loadResource("persona/animal/pig-java.svg")

val pigJsSvg: String = loadResource("persona/animal/pig-js.svg")

val pigNodeSvg: String = loadResource("persona/animal/pig-node.svg")

val pigSwiftSvg: String = loadResource("persona/animal/pig-swift.svg")

val pigLinuxSvg: String = loadResource("persona/animal/pig-linux.svg")

val pigSpringSvg: String = loadResource("persona/animal/pig-spring.svg")

val slimeRedSvg: String = loadResource("persona/animal/slime-red.svg")

val slimeRedKotlinSvg: String = loadResource("persona/animal/slime-red-kotlin.svg")

val slimeRedJavaSvg: String = loadResource("persona/animal/slime-red-java.svg")

val slimeRedJsSvg: String = loadResource("persona/animal/slime-red-js.svg")

val slimeRedNodeSvg: String = loadResource("persona/animal/slime-red-node.svg")

val slimeRedSwiftSvg: String = loadResource("persona/animal/slime-red-swift.svg")

val slimeRedLinuxSvg: String = loadResource("persona/animal/slime-red-linux.svg")

val slimeBlueSvg: String = loadResource("persona/animal/slime-blue.svg")

val slimeGreenSvg: String = loadResource("persona/animal/slime-green.svg")

val flamingoSvg: String = loadResource("persona/animal/flamingo.svg")

val tenmmSvg: String = loadResource("persona/animal/tenmm.svg")

val goblinSvg: String = loadResource("persona/animal/goblin.svg")

val goblinBagSvg: String = loadResource("persona/animal/goblin-bag.svg")

val bbibbiSvg: String = loadResource("persona/animal/bbibbi.svg")

val catSvg: String = loadResource("persona/animal/cat.svg")

val cheeseCatSvg: String = loadResource("persona/animal/cheese-cat.svg")

val galchiCatSvg: String = loadResource("persona/animal/galchi-cat.svg")

val whiteCatSvg: String = loadResource("persona/animal/white-cat.svg")

val fishManSvg: String = loadResource("persona/animal/fishman.svg")

val fishManGlassesSvg: String = loadResource("persona/animal/fishman-glasses.svg")

val quokkaSvg: String = loadResource("persona/animal/quokka.svg")

val quokkaLeafSvg: String = loadResource("persona/animal/quokka-leaf.svg")

val quokkaSunglassesSvg: String = loadResource("persona/animal/quokka-sunglasses.svg")

val moleSvg: String = loadResource("persona/animal/mole.svg")

val moleGrassSvg: String = loadResource("persona/animal/mole-grass.svg")

val rabbitSvg: String = loadResource("persona/animal/rabbit.svg")

val rabbitTubeSvg: String = loadResource("persona/animal/rabbit-tube.svg")

val pigCollaboratorSvg: String = loadResource("persona/animal/pig-collaborator.svg")

val cheeseCatCollaboratorSvg: String =
    loadResource("persona/animal/cheese-cat-collaborator.svg")

val whiteCatCollaboratorSvg: String = loadResource("persona/animal/white-cat-collaborator.svg")

val dessertFoxSvg: String = loadResource("persona/animal/dessert-fox.svg")

val dessertFoxCollaboratorSvg: String =
    loadResource("persona/animal/dessert-fox-collaborator.svg")

val slothSvg: String = loadResource("persona/animal/sloth.svg")

val slothKingSvg: String = loadResource("persona/animal/sloth-king.svg")

val slothSunglassesSvg: String = loadResource("persona/animal/sloth-sunglasses.svg")

val rabbitCollaboratorSvg: String = loadResource("persona/animal/rabbit-collaborator.svg")

val turtleSvg: String = loadResource("persona/animal/turtle.svg")

val ghostSvg: String = loadResource("persona/animal/ghost.svg")

val ghostKingSvg: String = loadResource("persona/animal/ghost-king.svg")

val screamSvg: String = loadResource("persona/animal/scream.svg")

val screamGhostSvg: String = loadResource("persona/animal/scream-ghost.svg")

val slimePumpkin1Svg: String = loadResource("persona/animal/slime-pumpkin-1.svg")

val slimePumpkin2Svg: String = loadResource("persona/animal/slime-pumpkin-2.svg")

val hamsterSvg: String = loadResource("persona/animal/hamster.svg")

val hamsterSnowSvg: String = loadResource("persona/animal/hamster-snow.svg")

val hamsterSnowSweetPotatoSvg: String = loadResource("persona/animal/hamster-snow-sweet-potato.svg")

val hamsterSpringSvg: String = loadResource("persona/animal/hamster-spring.svg")

val hamsterJavaSvg: String = loadResource("persona/animal/hamster-java.svg")

val hamsterKotlinSvg: String = loadResource("persona/animal/hamster-kotlin.svg")

val hamsterJsSvg: String = loadResource("persona/animal/hamster-js.svg")

val hamsterCollaboratorSvg: String = loadResource("persona/animal/hamster-collaborator.svg")

val hamsterTubeSvg: String = loadResource("persona/animal/hamster-tube.svg")

val snowmanSvg: String = loadResource("persona/animal/snowman.svg")

val snowmanMeltSvg: String = loadResource("persona/animal/snowman-melt.svg")

val littleChickSantaSvg: String = loadResource("persona/animal/little-chick-santa.svg")

val littleChickTubeSvg: String = loadResource("persona/animal/little-chick-tube.svg")

val hamsterSantaSvg: String = loadResource("persona/animal/hamster-santa.svg")

val dessertFoxRudolphSvg: String = loadResource("persona/animal/dessert-fox-rudolph.svg")

val rabbitBrownRudolphSvg: String = loadResource("persona/animal/rabbit-brown-rudolph.svg")

val malteseSvg: String = loadResource("persona/animal/maltese.svg")

val malteseCollaboratorSvg: String = loadResource("persona/animal/maltese-collaborator.svg")

val malteseKingSvg: String = loadResource("persona/animal/maltese-king.svg")

val ghostCollaboratorSvg: String = loadResource("persona/animal/ghost-collaborator.svg")

val unicornSvg: String = loadResource("persona/animal/unicorn.svg")

val shibaSvg: String = loadResource("persona/animal/shiba.svg")

val shibaKingSvg: String = loadResource("persona/animal/shiba-king.svg")

val capybaraSvg: String = loadResource("persona/animal/capybara.svg")

val capybaraCarrotSvg: String = loadResource("persona/animal/capybara-carrot.svg")

val capybaraSwimSvg: String = loadResource("persona/animal/capybara-swim.svg")

val littleChickEggOnHatSvg: String = loadResource("persona/animal/evolution/little-chick-egg-on-hat.svg")

val littleChickAngelSvg: String = loadResource("persona/animal/evolution/little-chick-angel.svg")

val flamingoWhiteSvg: String = loadResource("persona/animal/evolution/flamingo-white.svg")

val slimeAngelSvg: String = loadResource("persona/animal/evolution/slime-angel.svg")

val catWindowSvg: String = loadResource("persona/animal/evolution/cat-window.svg")

val gooseRobotSvg: String = loadResource("persona/animal/evolution/goose-robot.svg")

val pigRobotSvg: String = loadResource("persona/animal/evolution/pig-robot.svg")

val rudolphSvg: String = loadResource("persona/animal/rudolph.svg")

val largeTextSvgs = lazy {
    val map = mutableMapOf<String, String>()
    for (i in 'A'..'Z') {
        val path = "persona/text/large/$i.svg"
        map[i.toString()] = loadResource(path)
    }
    for (i in 'a'..'z') {
        val path = "persona/text/large/_$i.svg"
        map[i.toString()] = loadResource(path)
    }
    for (i in 0..9) {
        val path = "persona/text/large/$i.svg"
        map[i.toString()] = loadResource(path)
    }
    map["-"] = loadResource("persona/text/large/hyphens.svg")
    map
}.value

val largetTextAcceptableChars = lazy {
    val acceptableTitles = mutableListOf<Char>()
    for (i in 'A'..'Z') {
        acceptableTitles.add(i)
    }
    for (i in 'a'..'z') {
        acceptableTitles.add(i)
    }
    for (i in 0..9) {
        acceptableTitles.add(i.toChar())
    }
    acceptableTitles.add('-')
    acceptableTitles.toList()
}.value

val mediumNumberSvgs = lazy {
    val list = mutableListOf<String>()
    for (i in 0..9) {
        val path = "persona/text/medium/$i.svg"
        list.add(
            loadResource(path)
        )
    }
    list
}.value

val numberSvgs = lazy {
    val list = mutableListOf<String>()
    for (i in 0..9) {
        val path = "persona/text/small/$i.svg"
        list.add(
            loadResource(path)
        )
    }
    list
}.value
