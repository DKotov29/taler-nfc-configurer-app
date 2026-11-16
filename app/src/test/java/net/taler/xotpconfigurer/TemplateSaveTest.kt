/*
 * SPDX-FileCopyrightText: 2026 Taler Systems S.A.
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * This file is part of GNU Taler
 * (C) 2026 Taler Systems S.A.
 *
 * GNU Taler is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * GNU Taler is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with GNU Taler.  If not, see <https://www.gnu.org/licenses/>.
 */

package net.taler.xotpconfigurer

import net.taler.xotpconfigurer.data.SavedConfig
import net.taler.xotpconfigurer.data.SavedConfigKind
import net.taler.xotpconfigurer.model.SlotField
import net.taler.xotpconfigurer.model.uiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Field-by-field merging of the "Save Template" flow. */
class TemplateSaveTest {

    private val stored = slotValues(currency = "EUR", digits = "6", name = "Shop")
    private val current = slotValues(currency = "KUDOS", digits = "8", name = "Shop")
    private val save = TemplateSave(
        kind = SavedConfigKind.SLOT,
        values = current,
        returnTo = Screen.Overview,
        target = SavedConfig("Old", SavedConfigKind.SLOT, stored)
    )

    @Test
    fun differing_listsOnlyChangedFields() {
        assertEquals(
            listOf(SlotField.DIGITS, SlotField.CURRENCY).map { EditTarget.Slot(it) },
            save.differing
        )
    }

    @Test
    fun merge_defaultsToCurrentValues() {
        val (merged, errors) = save.merge()
        assertTrue(errors.isEmpty())
        assertEquals(current, merged)
    }

    @Test
    fun merge_appliesChoicePerField() {
        val (merged, errors) = save.copy(
            choices = mapOf(
                SlotField.DIGITS.name to MergeChoice(MergeSource.TEMPLATE),
                SlotField.CURRENCY.name to MergeChoice(MergeSource.CUSTOM, custom = " chf ")
            )
        ).merge()
        assertTrue(errors.isEmpty())
        assertEquals("6", merged[SlotField.DIGITS.name])
        assertEquals("CHF", merged[SlotField.CURRENCY.name]) // Normalized like on the device
        assertEquals("Shop", merged[SlotField.SLOT_NAME.name])
    }

    @Test
    fun merge_reportsInvalidCustomValues() {
        val (_, errors) = save.copy(
            choices = mapOf(SlotField.DIGITS.name to MergeChoice(MergeSource.CUSTOM, custom = "99"))
        ).merge()
        assertEquals(setOf(SlotField.DIGITS.name), errors.keys)
    }

    @Test
    fun templateName_mustBeUniqueAndNotBlank() {
        assertEquals(uiText(R.string.error_not_empty), templateNameError(" ", emptyList()))
        assertEquals(uiText(R.string.template_name_taken), templateNameError("shop ", listOf("Shop")))
        assertNull(templateNameError("Shop 2", listOf("Shop")))
    }

    private fun slotValues(currency: String, digits: String, name: String) = mapOf(
        SlotField.SLOT_NAME.name to name,
        SlotField.ALGORITHM.name to "1",
        SlotField.DIGITS.name to digits,
        SlotField.INTERVAL.name to "30",
        SlotField.CURRENCY.name to currency,
        SlotField.FRACTION.name to "100",
        SlotField.MERCHANT_TEMPLATE.name to ""
    )
}
