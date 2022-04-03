/*
 * Nextcloud Talk application
 *
 * @author Tobias Kaminsky
 * Copyright (C) 2020 Tobias Kaminsky
 * Copyright (C) 2020 Nextcloud GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.nextcloud.talk.databinding.PredefinedStatusBinding
import com.nextcloud.talk.models.json.status.predefined.PredefinedStatus

class PredefinedStatusListAdapter(
    private val clickListener: PredefinedStatusClickListener,
    val context: Context
) : RecyclerView.Adapter<PredefinedStatusViewHolder>() {
    internal var list: List<PredefinedStatus> = emptyList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PredefinedStatusViewHolder {
        val itemBinding = PredefinedStatusBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PredefinedStatusViewHolder(itemBinding)
    }

    override fun onBindViewHolder(holder: PredefinedStatusViewHolder, position: Int) {
        holder.bind(list[position], clickListener, context)
    }

    override fun getItemCount(): Int {
        return list.size
    }
}
