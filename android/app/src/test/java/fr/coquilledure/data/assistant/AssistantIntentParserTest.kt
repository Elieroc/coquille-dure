package fr.coquilledure.data.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantIntentParserTest {
    private fun parse(s: String) = AssistantIntentParser.parse(s)
    private fun AssistantIntent.words() = terms.map { it.norm }
    private fun AssistantIntent.out() = excludes.map { it.norm }

    @Test fun dinerRapidePoireaux() {
        val i = parse("une idée de dîner rapide avec des poireaux")
        assertEquals(listOf("poireaux"), i.words())
        assertEquals(DishKind.MEAL, i.dish)
        assertEquals(1800, i.maxTotalSeconds)
    }

    @Test fun queFaireCourgettes() {
        val i = parse("Que faire avec des courgettes ?")
        assertEquals(listOf("courgettes"), i.words())
        assertNull(i.dish)
        assertFalse(i.hasConstraint)
    }

    @Test fun dessertChocolatPourSix() {
        val i = parse("dessert au chocolat pour 6")
        assertEquals(DishKind.DESSERT, i.dish)
        assertEquals(listOf("chocolat"), i.words())
        assertEquals(6, i.servings)
    }

    @Test fun soupeVegetarienneSansCreme() {
        val i = parse("soupe végétarienne sans crème")
        assertEquals(DishKind.SOUP, i.dish)
        assertEquals(Diet.VEGETARIAN, i.diet)
        assertEquals(listOf("creme"), i.out())
        assertTrue(i.terms.isEmpty())
    }

    @Test fun sansPlusieursIngredients() {
        val i = parse("un plat sans oignon ni ail")
        assertEquals(listOf("oignon", "ail"), i.out())
        assertEquals(DishKind.MEAL, i.dish)
    }

    @Test fun sansViandeEtendLaListe() {
        val i = parse("gratin sans viande")
        assertEquals(listOf("gratin"), i.words())
        assertTrue("poulet" in i.out())
        assertTrue("jambon" in i.out())
    }

    @Test fun dureeEnMinutes() {
        assertEquals(1200, parse("un plat en 20 minutes").maxTotalSeconds)
        assertEquals(1800, parse("repas en moins de 30 min").maxTotalSeconds)
        assertEquals(2700, parse("gâteau 45min").maxTotalSeconds)
        assertEquals(5400, parse("plat en 1h30").maxTotalSeconds)
        assertEquals(3600, parse("plat en 1 heure").maxTotalSeconds)
    }

    @Test fun tresRapide() {
        assertEquals(900, parse("un dîner très rapide").maxTotalSeconds)
    }

    @Test fun personnes() {
        assertEquals(4, parse("un plat pour 4 personnes").servings)
        assertEquals(2, parse("un dîner pour deux").servings)
        assertEquals(1, parse("une recette pour une personne").servings)
        // « pour 20 minutes » n'est pas un nombre de personnes
        val i = parse("un plat pour 20 minutes")
        assertNull(i.servings)
        assertEquals(1200, i.maxTotalSeconds)
    }

    @Test fun petitDejeuner() {
        val i = parse("un petit-déjeuner gourmand")
        assertEquals(DishKind.BREAKFAST, i.dish)
        assertTrue(i.terms.isEmpty())
    }

    @Test fun apero() {
        assertEquals(DishKind.APERO, parse("Idées d'apéritif").dish)
    }

    @Test fun pommesDeTerre() {
        val i = parse("gratin de pommes de terre")
        assertEquals(listOf("gratin", "pommes", "terre"), i.words())
    }

    @Test fun maisIngredientEtConjonction() {
        assertEquals(listOf("maïs"), parse("salade de maïs").terms.map { it.shown }.filter { it == "maïs" })
        assertTrue(parse("poulet mais sans crème").words().none { it == "mais" })
    }

    @Test fun oeufsGardentLeurLigature() {
        val t = parse("omelette aux œufs").terms.first { it.stem == "oeuf" }
        assertEquals("œufs", t.shown)
    }

    @Test fun regimes() {
        assertEquals(Diet.VEGAN, parse("un plat vegan").diet)
        assertEquals(Diet.VEGETARIAN, parse("un plat végé").diet)
    }

    @Test fun facile() {
        assertTrue(parse("une recette facile de tarte").easy)
    }

    @Test fun nonFiltrableSignale() {
        val i = parse("un dessert sans gluten")
        assertEquals(listOf("sans gluten"), i.unsupported)
        assertTrue(i.out().isEmpty())
        assertEquals(DishKind.DESSERT, i.dish)
    }

    @Test fun affinageSansSujet() {
        val i = parse("plus rapide")
        assertFalse(i.hasSubject)
        assertEquals(1800, i.maxTotalSeconds)
        val merged = i.mergedOver(parse("soupe aux poireaux"))
        assertEquals(DishKind.SOUP, merged.dish)
        assertEquals(listOf("poireaux"), merged.words())
        assertEquals(1800, merged.maxTotalSeconds)
    }

    @Test fun affinageSansOignon() {
        val i = parse("sans oignon")
        assertFalse(i.hasSubject)
        assertEquals(listOf("oignon"), i.out())
    }

    @Test fun voirPlusDeRecettes() {
        assertTrue(parse("Voir d'autres recettes").more)
        assertTrue(parse("Rechercher plus de recettes").more)
        assertFalse(parse("plus de recettes avec du poulet").more)
    }

    @Test fun politesse() {
        assertEquals(Talk.GREETING, parse("Bonjour !").talk)
        assertEquals(Talk.THANKS, parse("merci beaucoup").talk)
        assertEquals(Talk.HELP, parse("aide").talk)
        assertNull(parse("bonjour, une soupe").talk)
    }

    @Test fun surprise() {
        assertTrue(parse("Surprends-moi").surprise)
        assertTrue(parse("que faire ce soir ?").dish == DishKind.MEAL)
    }

    @Test fun phraseVideOuBavarde() {
        val i = parse("je voudrais une idée s'il vous plaît")
        assertFalse(i.hasSubject)
        assertFalse(i.hasConstraint)
    }

    @Test fun phrasing() {
        val i = parse("dîner rapide végétarien avec des poireaux sans crème pour 4")
        assertEquals(
            "de plat rapide végétarien à base de poireaux, sans crème, pour 4 personnes",
            AssistantPhrasing.describe(i),
        )
        assertEquals("de soupe végétarienne", AssistantPhrasing.describe(parse("soupe végétarienne")))
    }

    @Test fun nomDePlatEtIngredient() {
        val i = parse("gratin de courgettes sans oignon")
        assertEquals(listOf("gratin"), i.terms.filter { it.isName }.map { it.norm })
        assertEquals(
            "de gratin à base de courgettes, sans oignon",
            AssistantPhrasing.describe(i),
        )
        assertEquals("de mousse à base de chocolat", AssistantPhrasing.describe(parse("mousse au chocolat")))
        assertEquals("d'omelette", AssistantPhrasing.describe(parse("une omelette")))
    }

    @Test fun accentsOptionnels() {
        val a = parse("soupe vegetarienne sans creme")
        val b = parse("soupe végétarienne sans crème")
        assertEquals(a.diet, b.diet)
        assertEquals(a.out(), b.out())
        assertEquals(a.dish, b.dish)
    }
}
