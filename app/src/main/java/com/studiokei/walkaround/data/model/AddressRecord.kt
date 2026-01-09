package com.studiokei.walkaround.data.model

import android.location.Address
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 住所情報を保持するデータモデル。
 * データベース保存用と、表示用のロジックを兼ね備えています。
 */
@Entity(
    tableName = "address_records",
    foreignKeys = [
        ForeignKey(
            entity = Section::class,
            parentColumns = ["sectionId"],
            childColumns = ["sectionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sectionId"])]
)
data class AddressRecord(
    // プライマリコンストラクタは Room が内部で使用する
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val time: Long = 0,
    val sectionId: Long? = null,
    val trackId: Long? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val name: String? = null,
    val addressLine: String? = null,
    val adminArea: String? = null,
    val countryName: String? = null,
    val locality: String? = null,
    val subLocality: String? = null,
    val thoroughfare: String? = null,
    val subThoroughfare: String? = null,
    val postalCode: String? = null
) {
    @Ignore
    var addressObj: Address? = null

    /**
     * Android の Address オブジェクトから AddressRecord を生成するための 2 次コンストラクタ。
     */
    @Ignore
    constructor(
        address: Address?,
        time: Long = 0,
        sectionId: Long? = null,
        trackId: Long? = null,
        lat: Double? = null,
        lng: Double? = null
    ) : this(
        id = 0,
        time = time,
        sectionId = sectionId,
        trackId = trackId,
        lat = lat,
        lng = lng,
        name = address?.let { addr ->
            val fName = addr.featureName
            // 数字、記号、空白以外の文字（漢字、かな、カナ、アルファベットなど）が含まれているかチェック
            // 全角・半角の数字および記号のみの文字列は除外する
            val filterRegex = Regex("[^0-9０-９\\p{Punct}\\p{IsPunctuation}\\s　]")
            if (fName != null && fName.contains(filterRegex)) fName else null
        },
        addressLine = address?.getAddressLine(0),
        adminArea = address?.adminArea,
        countryName = address?.countryName,
        locality = address?.locality,
        subLocality = address?.subLocality,
        thoroughfare = address?.thoroughfare,
        subThoroughfare = address?.subThoroughfare,
        postalCode = address?.postalCode
    ) {
        this.addressObj = address
    }

    /**
     * 住所の文字列（addressLine）から、都道府県などの広域な行政区画（adminArea）を削除し、
     * 市区町村以下から何丁目までの住所のみを抽出して表示する。
     */
    fun cityDisplay(): String? {
        if (addressLine == null) return null
        
        // 1. 都道府県などの広域行政区画を削除
        var display = if (adminArea != null) {
            val index = addressLine.indexOf(adminArea)
            if (index != -1) {
                addressLine.substring(index + adminArea.length).trim().removePrefix("、").removePrefix(",").trim()
            } else {
                addressLine
            }
        } else {
            addressLine
        }

        // 2. 末尾に name (建物名など) が含まれていたら削除（重複防止）
        if (!name.isNullOrBlank() && display.endsWith(name)) {
            display = display.removeSuffix(name).trim().removeSuffix("、").removeSuffix(",").trim()
        }

        // 3. thoroughfare が「丁目」を含む場合、表示文字列からも「丁目」以降をカット
        if (thoroughfare != null && thoroughfare.contains("丁目")) {
            val chomeIndex = display.indexOf("丁目")
            if (chomeIndex != -1) {
                display = display.substring(0, chomeIndex + 2) // "丁目" の2文字分までを含める
            }
        }

        // 4. 末尾が "数字-数字" 形式の場合、最初のハイフン以降を削除
        val pattern = Regex("([0-9０-９]+)(?:[-－−‐][0-9０-９]+)+$")
        display = display.replace(pattern, "$1")

        return display
    }

    /**
     * 都道府県以下の住所（名称なし）を返します。
     * 地点名（name）が住所文字列（addressLine）の末尾に含まれている場合は、分離のために除去します。
     */
    fun addressDisplay(): String? {
        if (addressLine == null) return null
        
        // 国名を除去
        var display = if (countryName != null) {
            val index = addressLine.indexOf(countryName)
            if (index != -1) {
                addressLine.substring(index + countryName.length).trim().removePrefix("、").removePrefix(",").trim()
            } else {
                addressLine
            }
        } else {
            addressLine
        }

        // 地点名（name）が住所の末尾に含まれている場合は、それを取り除く
        if (!name.isNullOrBlank() && display.endsWith(name)) {
            display = display.removeSuffix(name).trim().removeSuffix("、").removeSuffix(",").trim()
        }
        
        return display
    }

    /**
     * 表示用の地点名称（建物名など）を返します。
     * 住所本体（addressDisplay）の末尾と一致する場合や、名称が空の場合は null を返します。
     */
    fun featureNameDisplay(): String? {
        val baseAddress = addressDisplay()
        // 名称（name）が有効であり、かつ住所本体の末尾と一致しない場合のみ名称を返す
        return if (!name.isNullOrBlank() && baseAddress != null && !baseAddress.endsWith(name)) {
            name
        } else {
            null
        }
    }

    /**
     * 名称（建物名など）を含めた市区町村以下の住所表示を返します。
     * 音声読み上げ時に適切な「間」を空けるため、住所と名称の間には句点を入れています。
     */
    fun cityDisplayWithFeature(): String? {
        val cityAddress = cityDisplay() ?: return null
        val featureName = featureNameDisplay()
        
        return if (featureName != null) {
            // 読み上げ時に自然なポーズが入るよう、句点とスペースで区切る
            "$cityAddress。 $featureName"
        } else {
            cityAddress
        }
    }

    /**
     * 名称（建物名など）を含めた住所表示を返します。
     */
    fun addressDisplayWithFeature(): String? {
        val baseAddress = addressDisplay() ?: return null
        val featureName = featureNameDisplay()
        
        return if (featureName != null) {
            "$baseAddress\n$featureName"
        } else {
            baseAddress
        }
    }
}
