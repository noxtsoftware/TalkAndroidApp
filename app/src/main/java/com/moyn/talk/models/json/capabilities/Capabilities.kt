/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Tim Krüger
 * Copyright (C) 2022 Tim Krüger <t@timkrueger.me>
 * Copyright (C) 2017-2018 Mario Danic <mario@lovelyhq.com>
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
package com.moyn.talk.models.json.capabilities

import android.os.Parcelable
import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject
import kotlinx.android.parcel.Parcelize

@Parcelize
@JsonObject
data class Capabilities(
    @JsonField(name = ["spreed"])
    var spreedCapability: SpreedCapability?,
    @JsonField(name = ["notifications"])
    var notificationsCapability: NotificationsCapability?,
    @JsonField(name = ["theming"])
    var themingCapability: ThemingCapability?,
    @JsonField(name = ["external"])
    var externalCapability: HashMap<String, List<String>>?,
    @JsonField(name = ["provisioning_api"])
    var provisioningCapability: ProvisioningCapability?,
    @JsonField(name = ["user_status"])
    var userStatusCapability: UserStatusCapability?
) : Parcelable {
    // This constructor is added to work with the 'com.bluelinelabs.logansquare.annotation.JsonObject'
    constructor() : this(null, null, null, null, null, null)
}
