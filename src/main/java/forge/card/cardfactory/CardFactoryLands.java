/*
 * Forge: Play Magic: the Gathering.
 * Copyright (C) 2011  Forge Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package forge.card.cardfactory;

import java.util.List;

import forge.Card;
import forge.CardLists;
import forge.Command;
import forge.FThreads;
import forge.control.input.InputSelectCards;
import forge.control.input.InputSelectCardsFromList;
import forge.game.player.Player;
import forge.game.zone.ZoneType;
import forge.gui.GuiDialog;

/**
 * <p>
 * CardFactoryLands class.
 * </p>
 * 
 * @author Forge
 * @version $Id$
 */
class CardFactoryLands {

    /**
     * <p>
     * getCard.
     * </p>
     * 
     * @param card
     *            a {@link forge.Card} object.
     * @param cardName
     *            a {@link java.lang.String} object.
     * @param cf
     *            a {@link forge.card.cardfactory.CardFactoryInterface} object.
     * @return a {@link forge.Card} object.
     */
    public static void buildCard(final Card card, final String cardName) {

        // *************** START *********** START **************************
        // Ravinca Dual Lands
        if (cardName.equals("Blood Crypt") || cardName.equals("Breeding Pool") || cardName.equals("Godless Shrine")
                || cardName.equals("Hallowed Fountain") || cardName.equals("Overgrown Tomb")
                || cardName.equals("Sacred Foundry") || cardName.equals("Steam Vents")
                || cardName.equals("Stomping Ground") || cardName.equals("Temple Garden")
                || cardName.equals("Watery Grave")) {
            // if this isn't done, computer plays more than 1 copy
            card.clearSpellKeepManaAbility();

            card.addComesIntoPlayCommand(new Command() {
                private static final long serialVersionUID = 7352127748114888255L;

                @Override
                public void execute() {
                    if (card.getController().isHuman()) {
                        this.humanExecute();
                    } else {
                        this.computerExecute();
                    }
                }

                public void computerExecute() {
                    boolean needsTheMana = false;
                    final Player ai = card.getController();
                    if (ai.getLife() > 3 && ai.canPayLife(2)) {
                        final int landsize = ai.getLandsInPlay().size();
                        for (Card c : ai.getCardsIn(ZoneType.Hand)) {
                            if (landsize == c.getCMC()) {
                                needsTheMana = true;
                            }
                        }
                    }
                    if (needsTheMana) {
                        ai.payLife(2, card);
                    } else {
                        this.tapCard();
                    }
                }

                public void humanExecute() {
                    final Player human = card.getController();
                    if (human.canPayLife(2)) {

                        final String question = String.format("Pay 2 life? If you don't, %s enters the battlefield tapped.", card.getName());

                        if (GuiDialog.confirm(card, question.toString())) {
                            human.payLife(2, card);
                        } else {
                            this.tapCard();
                        }
                    } // if
                    else {
                        this.tapCard();
                    }
                } // execute()

                private void tapCard() {
                    // it enters the battlefield this way, and should not fire triggers
                    card.setTapped(true);
                }
            });
        } // *************** END ************ END **************************
    }

} // end class CardFactoryLands
