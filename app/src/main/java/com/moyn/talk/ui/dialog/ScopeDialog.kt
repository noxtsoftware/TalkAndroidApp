/*
 * Nextcloud Talk application
 *
 * @author Tobias Kaminsky
 * @author Andy Scherzinger
 * Copyright (C) 2021 Tobias Kaminsky <tobias.kaminsky@nextcloud.com>
 * Copyright (C) 2021 Andy Scherzinger <info@andy-scherzinger.de>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.moyn.talk.ui.dialog

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.moyn.talk.R
import com.moyn.talk.controllers.ProfileController
import com.moyn.talk.databinding.DialogScopeBinding
import com.moyn.talk.models.json.userprofile.Scope

class ScopeDialog(
    con: Context,
    private val userInfoAdapter: ProfileController.UserInfoAdapter,
    private val field: ProfileController.Field,
    private val position: Int
) :
    BottomSheetDialog(con) {

    private lateinit var dialogScopeBinding: DialogScopeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dialogScopeBinding = DialogScopeBinding.inflate(layoutInflater)
        setContentView(dialogScopeBinding.root)

        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        if (field == ProfileController.Field.DISPLAYNAME || field == ProfileController.Field.EMAIL) {
            dialogScopeBinding.scopePrivate.visibility = View.GONE
        }

        dialogScopeBinding.scopePrivate.setOnClickListener {
            userInfoAdapter.updateScope(position, Scope.PRIVATE)
            dismiss()
        }

        dialogScopeBinding.scopeLocal.setOnClickListener {
            userInfoAdapter.updateScope(position, Scope.LOCAL)
            dismiss()
        }

        dialogScopeBinding.scopeFederated.setOnClickListener {
            userInfoAdapter.updateScope(position, Scope.FEDERATED)
            dismiss()
        }

        dialogScopeBinding.scopePublished.setOnClickListener {
            userInfoAdapter.updateScope(position, Scope.PUBLISHED)
            dismiss()
        }
    }
}
