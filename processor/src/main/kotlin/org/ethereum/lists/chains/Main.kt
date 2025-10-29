package org.ethereum.lists.chains

import com.beust.klaxon.JsonArray
import com.beust.klaxon.JsonObject
import com.beust.klaxon.Klaxon
import okhttp3.OkHttpClient
import okhttp3.Request
import org.ethereum.lists.chains.model.*
import org.kethereum.erc55.isValid
import org.kethereum.model.Address
import org.kethereum.rpc.HttpEthereumRPC
import java.io.File
import java.io.FileReader
import java.math.BigInteger
import javax.imageio.ImageIO
import kotlin.io.OnErrorAction.SKIP

// --- Globals kept (minimized where possible) ---
private val parsedShortNames = mutableSetOf<String>()
private val parsedNames = mutableSetOf<String>()
private val allUsedIcons = mutableSetOf<String>()

private val basePath = File("..")
private val dataPath = File(basePath, "_data")
private val iconsPath = File(dataPath, "icons")
private val iconsDownloadPath = File(dataPath, "iconsDownload")
private val chainsPath = File(dataPath, "chains")

private val klaxon = Klaxon()
private val okHttpClient = OkHttpClient()

// Fail early if structure missing
private val allChainFiles: List<File> = chainsPath.listFiles()
    ?.filter { it.isFile }
    ?: error("${chainsPath.absolutePath} must contain the chain json files - but it does not")

private val allIconFiles: List<File> = iconsPath.listFiles()
    ?.filter { it.isFile }
    ?: error("${iconsPath.absolutePath} must contain the icon json files - but it does not")

// Constants
private val HTTP_PREFIXES = setOf("https://", "http://")
private val RPC_PREFIXES = setOf("https://", "http://", "wss://", "ws://")
private val NAME_REGEX = Regex("^[a-zA-Z0-9\\-.() ]+$")

fun main(args: Array<String>) {
    val argsList = args.toMutableList()
    val verbose = argsList.remove("verbose")

    when (argsList.firstOrNull()) {
        "singleChainCheck" -> {
            val last = argsList.lastOrNull() ?: return
            val file = File(File(".."), last)
            if (file.exists() && file.parentFile == chainsPath) {
                println("checking single chain $last")
                checkChain(file, onlineCheck = true, verbose = verbose)
            } else {
                error("File $last not found under ${chainsPath.absolutePath}")
            }
        }
        else -> {
            val online = argsList.firstOrNull() == "rpcConnect"
            val doIconDownload = argsList.firstOrNull() == "iconDownload"

            doChecks(onlineChecks = online, doIconDownload = doIconDownload, verbose = verbose)
            createOutputFiles()
        }
    }
}

private fun createOutputFiles() {
    val buildPath = File(basePath, "output").apply { mkdirs() }

    val chainJSONArray = JsonArray<JsonObject>()
    val miniChainJSONArray = JsonArray<JsonObject>()
    val chainIconJSONArray = JsonArray<JsonObject>()
    val shortNameMapping = JsonObject()

    // copy raw data so e.g. icons are available - SKIP errors
    File(basePath, "_data").copyRecursively(buildPath, onError = { _, _ -> SKIP })

    // Parse chains once, sort, then emit
    allChainFiles
        .asSequence()
        .map { file ->
            FileReader(file).use { reader -> klaxon.parseJsonObject(reader) }
        }
        .sortedBy { (it["chainId"] as Number).toLong() }
        .forEach { jsonObject ->
            chainJSONArray.add(jsonObject)

            val mini = JsonObject()
            listOf(
                "name", "chainId", "shortName", "networkId",
                "nativeCurrency", "rpc", "faucets", "infoURL"
            ).forEach { field ->
                jsonObject[field]?.let { mini[field] = it }
            }
            miniChainJSONArray.add(mini)

            shortNameMapping[jsonObject["shortName"] as String] =
                "eip155:${jsonObject["chainId"]}"
        }

    // Icons bundle
    allIconFiles.forEach { iconFile ->
        if (iconFile.extension != "json") error("Icon must be json $iconFile")
        val iconName = iconFile.nameWithoutExtension // faster/clearer

        val jsonData = FileReader(iconFile).use { reader ->
            klaxon.parseJsonArray(reader)
        }

        val iconJson = JsonObject().apply {
            this["name"] = iconName
            this["icons"] = jsonData
        }
        chainIconJSONArray.add(iconJson)
    }

    // Outputs
    File(buildPath, "chains.json").writeText(chainJSONArray.toJsonString())
    File(buildPath, "chains_pretty.json").writeText(chainJSONArray.toJsonString(prettyPrint = true))

    File(buildPath, "chains_mini.json").writeText(miniChainJSONArray.toJsonString())
    File(buildPath, "chains_mini_pretty.json").writeText(miniChainJSONArray.toJsonString(prettyPrint = true))

    File(buildPath, "chain_icons_mini.json").writeText(chainIconJSONArray.toJsonString())
    File(buildPath, "chain_icons.json").writeText(chainIconJSONArray.toJsonString(prettyPrint = true))

    File(buildPath, "shortNameMapping.json").writeText(shortNameMapping.toJsonString(prettyPrint = true))

    File(buildPath, ".nojekyll").createNewFile()
    File(buildPath, "CNAME").writeText("chainid.network")
}

private fun doChecks(onlineChecks: Boolean, doIconDownload: Boolean, verbose: Boolean) {
    // Chains
    allChainFiles.forEach { file ->
        try {
            checkChain(file, onlineChecks, verbose)
        } catch (e: Exception) {
            println("Problem with $file")
            throw e
        }
    }

    // Icons
    val allIcons = iconsPath.listFiles() ?: return
    val allIconCIDs = mutableSetOf<String>()
    allIcons.forEach { checkIcon(it, doIconDownload, allIconCIDs, verbose) }

    // Detect unreferenced downloaded icons
    val unusedIconDownload = mutableSetOf<String>()
    iconsDownloadPath.listFiles()?.forEach {
        if (!allIconCIDs.contains(it.name)) unusedIconDownload.add(it.name)
    }
    if (unusedIconDownload.isNotEmpty()) {
        throw UnreferencedIcon(unusedIconDownload.joinToString(" "), iconsDownloadPath)
    }

    // No directories allowed in chains dir
    chainsPath.listFiles()?.filter { it.isDirectory }?.forEach { _ ->
        error("chains directory must not contain sub-directories")
    }

    // Detect unused icon descriptors
    val unusedIcons = mutableSetOf<String>()
    iconsPath.listFiles()?.forEach {
        val base = it.nameWithoutExtension
        if (!allUsedIcons.contains(base)) unusedIcons.add(it.toString())
    }
    if (unusedIcons.isNotEmpty()) {
        error("error: unused icons ${unusedIcons.joinToString(" ")}")
    }
}

private fun checkIcon(icon: File, withIconDownload: Boolean, allIconCIDs: MutableSet<String>, verbose: Boolean) {
    val arr: JsonArray<*> = FileReader(icon).use { reader -> klaxon.parseJsonArray(reader) }
    if (verbose) {
        println("checking Icon ${icon.name}")
        println("found variants ${arr.size}")
    }

    arr.forEach { variant ->
        val obj = variant as? JsonObject ?: error("Icon variant must be an object")

        val url = obj["url"] as? String ?: error("Icon must have a URL")
        require(url.startsWith("ipfs://")) { "url must start with ipfs://" }

        val iconCID = url.removePrefix("ipfs://")
        allIconCIDs.add(iconCID)

        val iconDownloadFile = File(iconsDownloadPath, iconCID)

        if (!iconDownloadFile.exists() && withIconDownload) {
            try {
                println("fetching Icon from IPFS $iconCID")
                val iconBytes = ipfs.get.catBytes(iconCID)
                println("Icon size ${iconBytes.size}")
                iconDownloadFile.writeBytes(iconBytes)
            } catch (e: Exception) {
                println("could not fetch icon from IPFS: ${e.message}")
            }
        }

        val width = obj["width"]
        val height = obj["height"]
        if (width != null || height != null) {
            require(width is Int && height is Int) {
                "If icon has width/height it needs both and must be Int"
            }
        }

        val format = obj["format"] as? String
        require(format in setOf("png", "svg", "jpg")) {
            "Icon format must be png, svg or jpg but was $format"
        }

        if (iconDownloadFile.exists()) {
            try {
                // Determine actual format
                val actualFormat = iconDownloadFile.inputStream().use { ins ->
                    ImageIO.createImageInputStream(ins).use { iis ->
                        val reader = ImageIO.getImageReaders(iis).asSequence().firstOrNull()
                            ?: error("No ImageReader for $iconDownloadFile")
                        reader.formatName.replace("JPEG", "jpg")
                    }
                }

                // Load image to check dimensions
                val image = ImageIO.read(iconDownloadFile)
                    ?: error("Could not read image $iconDownloadFile")

                if (actualFormat != format) {
                    error("format in json ($icon) is $format but found $actualFormat in imageDownload")
                }
                if (width is Int && image.width != width) {
                    error("width in json ($icon) is $width but actual is ${image.width}")
                }
                if (height is Int && image.raster.height != height) {
                    error("height in json ($icon) is $height but actual is ${image.height}")
                }

                if (!legacyCIDs.contains(iconDownloadFile.name)) {
                    val fileSize = iconDownloadFile.length()
                    if (fileSize > 250 * 1024) error("icon is bigger than 250kb")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                error("problem with image $iconDownloadFile")
            }
        }
    }
}

private fun checkChain(chainFile: File, onlineCheck: Boolean, verbose: Boolean = false) {
    if (verbose) println("processing $chainFile")

    val jsonObject = FileReader(chainFile).use { reader ->
        klaxon.parseJsonObject(reader)
    }

    val chainIdAsLong = getNumber(jsonObject, "chainId")

    // File name / namespace checks
    val expectedPrefix = "eip155-"
    require(chainFile.nameWithoutExtension.startsWith(expectedPrefix)) { "Unsupported namespace" }
    val fromName = chainFile.nameWithoutExtension.removePrefix(expectedPrefix)
    if (chainIdAsLong.toString() != fromName) throw FileNameMustMatchChainId()
    require(chainFile.extension == "json") { throw ExtensionMustBeJSON() }

    // Required/extra fields
    getNumber(jsonObject, "networkId")
    val extraFields = jsonObject.map.keys.subtract(mandatory_fields).subtract(optionalFields)
    if (extraFields.isNotEmpty()) throw ShouldHaveNoExtraFields(extraFields)

    val missingFields = mandatory_fields.subtract(jsonObject.map.keys)
    if (missingFields.isNotEmpty()) throw ShouldHaveNoMissingFields(missingFields)

    jsonObject["icon"]?.let { processIcon(it, chainFile) }

    jsonObject["nativeCurrency"]?.let {
        if (it !is JsonObject) throw NativeCurrencyMustBeObject()
        val symbol = it["symbol"] as? String ?: throw NativeCurrencySymbolMustBeString()
        if (symbol.trim() != symbol) throw NativeCurrencyCantBeTrimmed()
        if (symbol.length >= 7) throw NativeCurrencySymbolMustHaveLessThan7Chars()
        if (it.keys != setOf("symbol", "decimals", "name")) throw NativeCurrencyCanOnlyHaveSymbolNameAndDecimals()
        if (it["decimals"] !is Int) throw NativeCurrencyDecimalMustBeInt()
        val currencyName = it["name"] as? String ?: throw NativeCurrencyNameMustBeString()
        if (!NAME_REGEX.matches(currencyName)) throw IllegalName("currencyName", currencyName)
    }

    val chainName = jsonObject["name"] as? String ?: throw ChainNameMustBeString()
    if (!NAME_REGEX.matches(chainName)) throw IllegalName("chain name", chainName)

    jsonObject["explorers"]?.let { explorers ->
        if (explorers !is JsonArray<*>) throw ExplorersMustBeArray()
        explorers.forEach { explorerAny ->
            val explorer = explorerAny as? JsonObject ?: error("explorer must be object")
            if (explorer["name"] == null) throw ExplorerMustHaveName()

            explorer["icon"]?.let { explorerIcon -> processIcon(explorerIcon, chainFile) }

            val url = explorer["url"] as? String ?: throw ExplorerMustWithHttpsOrHttp()
            if (HTTP_PREFIXES.none { prefix -> url.startsWith(prefix) }) throw ExplorerMustWithHttpsOrHttp()
            if (url.endsWith("/")) throw ExplorerCannotEndInSlash()
            url.checkString("Explorer URL")

            val standard = explorer["standard"]
            if (standard != "EIP3091" && standard != "none") throw ExplorerStandardMustBeEIP3091OrNone()

            if (onlineCheck) {
                val request = Request.Builder().url(url).build()
                okHttpClient.newCall(request).execute().use { resp ->
                    val code = resp.code
                    if (code / 100 != 2 && code != 403) { // allow 403 (Cloudflare)
                        throw CantReachExplorerException(url, code)
                    }
                }
            }
        }
    }

    jsonObject["ens"]?.let {
        if (it !is JsonObject) throw ENSMustBeObject()
        if (it.keys != setOf("registry")) throw ENSMustHaveOnlyRegistry()
        val address = Address(it["registry"] as String)
        if (!address.isValid()) throw ENSRegistryAddressMustBeValid()
    }

    jsonObject["status"]?.let { status ->
        if (status !is String) throw StatusMustBeString()
        if (status !in setOf("incubating", "active", "deprecated")) throw StatusMustBeIncubatingActiveOrDeprecated()
    }

    jsonObject["faucets"]?.let { faucets ->
        if (faucets !is List<*>) throw FaucetsMustBeArray()
        faucets.forEach {
            if (it !is String) throw FaucetMustBeString()
            it.checkString("Faucet URL")
        }
    }

    jsonObject["redFlags"]?.let { redFlags ->
        if (redFlags !is List<*>) throw RedFlagsMustBeArray()
        redFlags.forEach {
            if (it !is String) throw RedFlagMustBeString()
            it.checkString("Red flag")
            if (!allowedRedFlags.contains(it)) throw InvalidRedFlags(it)
        }
    }

    jsonObject["parent"]?.let { parentAny ->
        val parent = parentAny as? JsonObject ?: throw ParentMustBeObject()
        if (!parent.keys.containsAll(setOf("chain", "type"))) throw ParentMustHaveChainAndType()

        val extraParentFields = parent.keys - setOf("chain", "type", "bridges")
        if (extraParentFields.isNotEmpty()) throw ParentHasExtraFields(extraParentFields)

        val bridges = parent["bridges"]
        if (bridges != null && bridges !is List<*>) throw ParentBridgeNoArray()
        (bridges as? JsonArray<*>)?.forEach { bridge ->
            if (bridge !is JsonObject) throw BridgeNoObject()
            if (bridge.keys.size != 1 || bridge.keys.first() != "url") throw BridgeOnlyURL()
        }

        if (parent["type"] !in setOf("L2", "shard")) {
            throw ParentHasInvalidType(parent["type"] as? String)
        }

        val parentFile = File(chainFile.parentFile, "${parent["chain"]}.json")
        if (!parentFile.exists()) throw ParentChainDoesNotExist(parent["chain"] as String)
    }

    // Moshi parse validates trailing commas etc.
    parseWithMoshi(chainFile)

    val rpcList = jsonObject["rpc"] as? List<*> ?: throw RPCMustBeList()
    rpcList.forEach { rpcURL ->
        if (rpcURL !is String) throw RPCMustBeListOfStrings()
        if (RPC_PREFIXES.none { rpcURL.startsWith(it) }) throw InvalidRPCPrefix(rpcURL)
        rpcURL.checkString("RPC URL")

        if (onlineCheck) {
            var chainId: BigInteger? = null
            try {
                println("connecting to $rpcURL")
                val ethereumRPC = HttpEthereumRPC(rpcURL)
                println("Client:${ethereumRPC.clientVersion()}")
                println("BlockNumber:${ethereumRPC.blockNumber()}")
                println("GasPrice:${ethereumRPC.gasPrice()}")
                chainId = ethereumRPC.chainId()?.value
            } catch (_: Exception) {
                // tolerate offline endpoints
            }
            chainId?.let { nonNull ->
                if (chainIdAsLong != nonNull.toLong()) {
                    error("RPC chainId (${nonNull.toLong()}) does not match chainId from json ($chainIdAsLong)")
                }
            }
        }
    }
}

private fun processIcon(value: Any, chainFile: File): Boolean {
    val iconName = value as? String ?: error("icon must be string")
    val iconFile = File(iconsPath, "$iconName.json")
    if (!iconFile.exists()) {
        error("The Icon $iconName does not exist - used in ${chainFile.name}")
    }
    return allUsedIcons.add(iconName)
}

private fun String.checkString(which: String) {
    if (isBlank()) throw StringCannotBeBlank(which)
    if (trim() != this) throw StringCannotHaveExtraSpaces(which)
}

private fun String.normalizeName() = replace(" ", "").uppercase()

/*
moshi fails for extra commas
https://github.com/ethereum-lists/chains/issues/126
*/
private fun parseWithMoshi(fileToParse: File) {
    val parsedChain = chainAdapter.fromJson(fileToParse.readText()) ?: error("Cannot parse ${fileToParse.name}")
    val parsedChainNormalizedName = parsedChain.name.normalizeName()
    if (!parsedNames.add(parsedChainNormalizedName)) {
        throw NameMustBeUnique(parsedChainNormalizedName)
    }

    val parsedChainNormalizedShortName = parsedChain.shortName.normalizeName()
    if (parsedChainNormalizedShortName == "*") throw ShortNameMustNotBeStar()
    if (!parsedShortNames.add(parsedChainNormalizedShortName)) {
        throw ShortNameMustBeUnique(parsedChainNormalizedShortName)
    }
}

private fun getNumber(jsonObject: JsonObject, field: String): Long =
    when (val v = jsonObject[field]) {
        is Int -> v.toLong()
        is Long -> v
        is Number -> v.toLong()
        else -> throw Exception("not a number at $field")
    }
