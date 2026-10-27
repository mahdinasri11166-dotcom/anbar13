package com.example.anbar

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * بخش ۱: مدیریت پیشرفته انبار
 * - چند انبار
 * - واریانت (سایز/رنگ)
 * - سری ساخت و انقضا
 * - واحد اندازه‌گیری
 */
class InventoryRepository(context: Context) {

    /** آخرین پیام خطای قابل‌نمایش به کاربر */
    var lastError: String = ""
        private set

    private val prefs: SharedPreferences =
        context.getSharedPreferences("anbar_prefs_v2", Context.MODE_PRIVATE)

    private var nextId: Long
        get() = prefs.getLong("next_id", 1L)
        set(value) = prefs.edit().putLong("next_id", value).apply()

    private fun next(): Long {
        val id = nextId
        nextId = id + 1
        return id
    }

    // ---------- Warehouse ----------
    fun getWarehouses(): List<Warehouse> {
        val json = prefs.getString("warehouses", null)
        if (json == null) {
            val defaults = listOf(
                Warehouse(next(), "انبار مغازه", "MAIN", true),
                Warehouse(next(), "انبار پشتی", "BACKUP", false)
            )
            saveWarehouses(defaults)
            return defaults
        }
        val parsed = parseWarehouses(json)
        if (parsed.isEmpty()) {
            val defaults = listOf(
                Warehouse(next(), "انبار مغازه", "MAIN", true),
                Warehouse(next(), "انبار پشتی", "BACKUP", false)
            )
            saveWarehouses(defaults)
            return defaults
        }
        return parsed
    }

    fun addWarehouse(name: String, type: String = "OTHER"): Warehouse {
        val list = getWarehouses().toMutableList()
        val w = Warehouse(next(), name, type, false)
        list.add(w)
        saveWarehouses(list)
        return w
    }

    fun getDefaultWarehouse(): Warehouse {
        val list = getWarehouses()
        return list.firstOrNull { it.isDefault } ?: list.firstOrNull()
            ?: Warehouse(1, "انبار اصلی", "MAIN", true)
    }

    private fun saveWarehouses(list: List<Warehouse>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id)
                put("name", it.name)
                put("type", it.type)
                put("isDefault", it.isDefault)
            })
        }
        prefs.edit().putString("warehouses", arr.toString()).apply()
    }

    private fun parseWarehouses(json: String): List<Warehouse> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Warehouse(
                    o.getLong("id"),
                    o.optString("name", "انبار"),
                    o.optString("type", "MAIN"),
                    o.optBoolean("isDefault", false)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ---------- Product ----------
    fun getProducts(): List<Product> {
        return try {
            val json = prefs.getString("products", "[]") ?: "[]"
            val arr = JSONArray(json)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Product(
                    id = o.getLong("id"),
                    name = o.optString("name", "کالا"),
                    code = o.optString("code", ""),
                    barcode = o.optString("barcode", ""),
                    unit = o.optString("unit", "عدد"),
                    hasVariants = o.optBoolean("hasVariants", false),
                    trackBatch = o.optBoolean("trackBatch", false),
                    description = o.optString("description", ""),
                    purchasePrice = o.optDouble("purchasePrice", 0.0),
                    salePrice = o.optDouble("salePrice", 0.0),
                    category = o.optString("category", "عمومی"),
                    imagePath = o.optString("imagePath", ""),
                    minStock = o.optDouble("minStock", 0.0),
                    maxStock = o.optDouble("maxStock", 0.0)
                )
            }.sortedBy { it.name }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getProduct(id: Long): Product? = getProducts().find { it.id == id }

    fun addProduct(
        name: String,
        code: String,
        barcode: String = "",
        unit: String = "عدد",
        hasVariants: Boolean = false,
        trackBatch: Boolean = false,
        purchasePrice: Double = 0.0,
        salePrice: Double = 0.0,
        category: String = "عمومی",
        imagePath: String = ""
    ): Product {
        touchAppClock()
        val list = getProducts().toMutableList()
        val p = Product(
            next(), name.trim(), code.trim(), barcode.trim(), unit,
            hasVariants, trackBatch, "", purchasePrice, salePrice, category, imagePath
        )
        list.add(p)
        saveProducts(list)
        // ثبت قیمت فروش در جدول قیمت‌ها
        if (salePrice > 0) {
            setProductPrice(ProductPrice(p.id, retail = salePrice, wholesale = salePrice * 0.9, colleague = salePrice * 0.85))
        }
        return p
    }

    fun updateProduct(p: Product) {
        val list = getProducts().toMutableList()
        val idx = list.indexOfFirst { it.id == p.id }
        if (idx >= 0) {
            list[idx] = p
            saveProducts(list)
        }
    }

    fun deleteProduct(id: Long) {
        saveProducts(getProducts().filter { it.id != id })
        saveVariants(getVariants().filter { it.productId != id })
        saveStock(getAllStock().filter { it.productId != id })
    }

    private fun saveProducts(list: List<Product>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id)
                put("name", it.name)
                put("code", it.code)
                put("barcode", it.barcode)
                put("unit", it.unit)
                put("hasVariants", it.hasVariants)
                put("trackBatch", it.trackBatch)
                put("description", it.description)
                put("purchasePrice", it.purchasePrice)
                put("salePrice", it.salePrice)
                put("category", it.category)
                put("imagePath", it.imagePath)
                put("minStock", it.minStock)
                put("maxStock", it.maxStock)
            })
        }
        prefs.edit().putString("products", arr.toString()).apply()
    }

    // ---------- Variants ----------
    fun getVariants(productId: Long? = null): List<ProductVariant> {
        val json = prefs.getString("variants", "[]") ?: "[]"
        val arr = JSONArray(json)
        val all = (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            ProductVariant(
                id = o.getLong("id"),
                productId = o.getLong("productId"),
                name = o.getString("name"),
                color = o.optString("color", ""),
                size = o.optString("size", ""),
                barcode = o.optString("barcode", ""),
                sku = o.optString("sku", "")
            )
        }
        return if (productId != null) all.filter { it.productId == productId } else all
    }

    fun addVariant(
        productId: Long,
        color: String,
        size: String,
        barcode: String = "",
        sku: String = ""
    ): ProductVariant {
        val name = listOf(color, size).filter { it.isNotBlank() }.joinToString(" - ").ifEmpty { "پیش‌فرض" }
        val v = ProductVariant(next(), productId, name, color, size, barcode, sku)
        val list = getVariants().toMutableList()
        list.add(v)
        saveVariants(list)
        getProduct(productId)?.let { updateProduct(it.copy(hasVariants = true)) }
        return v
    }

    private fun saveVariants(list: List<ProductVariant>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id)
                put("productId", it.productId)
                put("name", it.name)
                put("color", it.color)
                put("size", it.size)
                put("barcode", it.barcode)
                put("sku", it.sku)
            })
        }
        prefs.edit().putString("variants", arr.toString()).apply()
    }

    // ---------- Stock ----------
    fun getAllStock(): List<StockItem> {
        val json = prefs.getString("stock", "[]") ?: "[]"
        val arr = JSONArray(json)
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            StockItem(
                id = o.getLong("id"),
                productId = o.getLong("productId"),
                variantId = if (o.isNull("variantId")) null else o.getLong("variantId"),
                warehouseId = o.getLong("warehouseId"),
                quantity = o.getDouble("quantity"),
                batchNumber = o.optString("batchNumber", ""),
                expiryDate = o.optLong("expiryDate", 0L),
                unitCost = o.optDouble("unitCost", 0.0)
            )
        }
    }

    fun findStock(
        productId: Long,
        warehouseId: Long,
        variantId: Long? = null,
        batchNumber: String = ""
    ): StockItem? {
        return getAllStock().find {
            it.productId == productId &&
                    it.warehouseId == warehouseId &&
                    it.variantId == variantId &&
                    it.batchNumber == batchNumber
        }
    }

    fun entry(
        productId: Long,
        warehouseId: Long,
        amount: Double,
        variantId: Long? = null,
        batchNumber: String = "",
        expiryDate: Long = 0L,
        note: String = ""
    ): Boolean {
        touchAppClock()
        if (amount <= 0) return false
        val all = getAllStock().toMutableList()
        val existing = findStock(productId, warehouseId, variantId, batchNumber)
        if (existing != null) {
            val updated = existing.copy(quantity = existing.quantity + amount)
            val idx = all.indexOfFirst { it.id == existing.id }
            all[idx] = updated
        } else {
            all.add(
                StockItem(
                    id = next(),
                    productId = productId,
                    variantId = variantId,
                    warehouseId = warehouseId,
                    quantity = amount,
                    batchNumber = batchNumber,
                    expiryDate = expiryDate
                )
            )
        }
        saveStock(all)
        val product = getProduct(productId)
        addTransaction(productId, variantId, warehouseId, product?.name ?: "", "ENTRY", amount, batchNumber, note)
        return true
    }

    fun exit(
        productId: Long,
        warehouseId: Long,
        amount: Double,
        variantId: Long? = null,
        batchNumber: String = "",
        note: String = ""
    ): Boolean {
        if (amount <= 0) return false
        val all = getAllStock().toMutableList()
        val existing = findStock(productId, warehouseId, variantId, batchNumber) ?: return false
        if (existing.quantity < amount) return false
        val updated = existing.copy(quantity = existing.quantity - amount)
        val idx = all.indexOfFirst { it.id == existing.id }
        if (updated.quantity <= 0.0001) {
            all.removeAt(idx)
        } else {
            all[idx] = updated
        }
        saveStock(all)
        val product = getProduct(productId)
        addTransaction(productId, variantId, warehouseId, product?.name ?: "", "EXIT", amount, batchNumber, note)
        return true
    }

    fun transfer(
        productId: Long,
        fromWarehouseId: Long,
        toWarehouseId: Long,
        amount: Double,
        variantId: Long? = null,
        batchNumber: String = "",
        note: String = ""
    ): Boolean {
        if (fromWarehouseId == toWarehouseId || amount <= 0) return false
        val ok = exit(productId, fromWarehouseId, amount, variantId, batchNumber, "انتقال: $note")
        if (!ok) return false
        entry(productId, toWarehouseId, amount, variantId, batchNumber, 0L, "انتقال از انبار دیگر")
        val transfers = getTransfers().toMutableList()
        transfers.add(
            StockTransfer(
                id = next(),
                fromWarehouseId = fromWarehouseId,
                toWarehouseId = toWarehouseId,
                productId = productId,
                variantId = variantId,
                quantity = amount,
                batchNumber = batchNumber,
                note = note,
                timestamp = System.currentTimeMillis()
            )
        )
        saveTransfers(transfers)
        return true
    }

    private fun saveStock(list: List<StockItem>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id)
                put("productId", it.productId)
                if (it.variantId != null) put("variantId", it.variantId) else put("variantId", JSONObject.NULL)
                put("warehouseId", it.warehouseId)
                put("quantity", it.quantity)
                put("batchNumber", it.batchNumber)
                put("expiryDate", it.expiryDate)
                put("unitCost", it.unitCost)
            })
        }
        prefs.edit().putString("stock", arr.toString()).apply()
    }

    fun getTransfers(): List<StockTransfer> {
        val json = prefs.getString("transfers", "[]") ?: "[]"
        val arr = JSONArray(json)
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            StockTransfer(
                id = o.getLong("id"),
                fromWarehouseId = o.getLong("fromWarehouseId"),
                toWarehouseId = o.getLong("toWarehouseId"),
                productId = o.getLong("productId"),
                variantId = if (o.isNull("variantId")) null else o.getLong("variantId"),
                quantity = o.getDouble("quantity"),
                batchNumber = o.optString("batchNumber", ""),
                note = o.optString("note", ""),
                timestamp = o.getLong("timestamp")
            )
        }.sortedByDescending { it.timestamp }
    }

    private fun saveTransfers(list: List<StockTransfer>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id)
                put("fromWarehouseId", it.fromWarehouseId)
                put("toWarehouseId", it.toWarehouseId)
                put("productId", it.productId)
                if (it.variantId != null) put("variantId", it.variantId) else put("variantId", JSONObject.NULL)
                put("quantity", it.quantity)
                put("batchNumber", it.batchNumber)
                put("note", it.note)
                put("timestamp", it.timestamp)
            })
        }
        prefs.edit().putString("transfers", arr.toString()).apply()
    }

    private fun addTransaction(
        productId: Long,
        variantId: Long?,
        warehouseId: Long,
        productName: String,
        type: String,
        amount: Double,
        batchNumber: String,
        note: String
    ) {
        val list = getTransactions().toMutableList()
        list.add(
            Transaction(
                id = next(),
                productId = productId,
                variantId = variantId,
                warehouseId = warehouseId,
                productName = productName,
                type = type,
                amount = amount,
                batchNumber = batchNumber,
                note = note,
                timestamp = System.currentTimeMillis()
            )
        )
        saveTransactions(list)
    }

    fun getTransactions(): List<Transaction> {
        val json = prefs.getString("transactions", "[]") ?: "[]"
        val arr = JSONArray(json)
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Transaction(
                id = o.getLong("id"),
                productId = o.getLong("productId"),
                variantId = if (o.isNull("variantId")) null else o.getLong("variantId"),
                warehouseId = o.getLong("warehouseId"),
                productName = o.getString("productName"),
                type = o.getString("type"),
                amount = o.getDouble("amount"),
                batchNumber = o.optString("batchNumber", ""),
                note = o.optString("note", ""),
                timestamp = o.getLong("timestamp")
            )
        }.sortedByDescending { it.timestamp }
    }

    private fun saveTransactions(list: List<Transaction>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id)
                put("productId", it.productId)
                if (it.variantId != null) put("variantId", it.variantId) else put("variantId", JSONObject.NULL)
                put("warehouseId", it.warehouseId)
                put("productName", it.productName)
                put("type", it.type)
                put("amount", it.amount)
                put("batchNumber", it.batchNumber)
                put("note", it.note)
                put("timestamp", it.timestamp)
            })
        }
        prefs.edit().putString("transactions", arr.toString()).apply()
    }

    fun getInventoryRows(warehouseId: Long? = null, daysBeforeExpiryAlert: Int = 30): List<InventoryRow> {
        val products = getProducts().associateBy { it.id }
        val variants = getVariants().associateBy { it.id }
        val warehouses = getWarehouses().associateBy { it.id }
        val now = System.currentTimeMillis()
        val alertMs = daysBeforeExpiryAlert * 24L * 60 * 60 * 1000

        return getAllStock()
            .filter { warehouseId == null || it.warehouseId == warehouseId }
            .mapNotNull { s ->
                val p = products[s.productId] ?: return@mapNotNull null
                val v = s.variantId?.let { variants[it] }
                val w = warehouses[s.warehouseId] ?: return@mapNotNull null
                val isExpired = s.expiryDate > 0 && s.expiryDate < now
                val isExpiringSoon = s.expiryDate > 0 && !isExpired && s.expiryDate - now < alertMs
                InventoryRow(
                    stockId = s.id,
                    productId = s.productId,
                    variantId = s.variantId,
                    productName = p.name,
                    variantName = v?.name ?: "",
                    warehouseName = w.name,
                    quantity = s.quantity,
                    unit = p.unit,
                    batchNumber = s.batchNumber,
                    expiryDate = s.expiryDate,
                    barcode = v?.barcode?.ifEmpty { p.barcode } ?: p.barcode,
                    isExpiringSoon = isExpiringSoon,
                    isExpired = isExpired
                )
            }
            .sortedWith(compareBy({ it.productName }, { it.variantName }, { it.warehouseName }))
    }

    fun findByBarcode(barcode: String): Pair<Product, ProductVariant?>? {
        if (barcode.isBlank()) return null
        getVariants().find { it.barcode == barcode }?.let { v ->
            getProduct(v.productId)?.let { return it to v }
        }
        getProducts().find { it.barcode == barcode }?.let { return it to null }
        return null
    }

    fun getExpiringItems(days: Int = 30): List<InventoryRow> {
        return getInventoryRows().filter { it.isExpiringSoon || it.isExpired }
    }

    // ==================== بخش ۲: فروش و مشتری ====================

    fun getSettings(): AppSettings {
        val shop = prefs.getString("shop_name", "فروشگاه من") ?: "فروشگاه من"
        val vat = prefs.getFloat("vat_percent", 9f).toDouble()
        val currency = prefs.getString("currency", "TOMAN") ?: "TOMAN"
        val color = prefs.getString("primary_color", "#1565C0") ?: "#1565C0"
        val logo = prefs.getString("logo_path", "") ?: ""
        val footer = prefs.getString("invoice_footer", "از خرید شما سپاسگزاریم") ?: "از خرید شما سپاسگزاریم"
        val shortcuts = prefs.getString("shortcuts", "POS,CUSTOMERS,DASHBOARD") ?: "POS,CUSTOMERS,DASHBOARD"
        val usd = prefs.getFloat("rate_usd_toman", 900000f).toDouble()
        val usdt = prefs.getFloat("rate_usdt_toman", 900000f).toDouble()
        val addr = prefs.getString("shop_address", "") ?: ""
        val phone = prefs.getString("shop_phone", "") ?: ""
        val cal = prefs.getString("calendar_type", "SHAMSI") ?: "SHAMSI"
        val cashierOnly = prefs.getBoolean("cashier_only", false)
        val autoLogout = prefs.getInt("auto_logout_min", 0)
        val invPoetry = prefs.getBoolean("invoice_poetry", true)
        val invExtra = prefs.getString("invoice_extra_note", "") ?: ""
        val s = AppSettings(shop, vat, currency, 0L, color, logo, footer, shortcuts, usd, usdt, addr, phone, cal, cashierOnly, autoLogout, invPoetry, invExtra)
        FormatUtils.applyFromSettings(s)
        return s
    }

    fun saveSettings(s: AppSettings) {
        prefs.edit()
            .putString("shop_name", s.shopName)
            .putFloat("vat_percent", s.vatPercent.toFloat())
            .putString("currency", s.currency)
            .putString("primary_color", s.primaryColor)
            .putString("logo_path", s.logoPath)
            .putString("invoice_footer", s.invoiceFooter.ifBlank { "از خرید شما متشکریم" })
            .putString("shortcuts", s.shortcuts)
            .putFloat("rate_usd_toman", s.rateUsdToToman.toFloat())
            .putFloat("rate_usdt_toman", s.rateUsdtToToman.toFloat())
            .putString("shop_address", s.shopAddress)
            .putString("shop_phone", s.shopPhone)
            .putString("calendar_type", if (s.calendarType.uppercase().contains("GREG")) "GREGORIAN" else "SHAMSI")
            .putBoolean("cashier_only", s.cashierOnlyMode)
            .putInt("auto_logout_min", s.autoLogoutMinutes)
            .putBoolean("invoice_poetry", s.invoiceShowPoetry)
            .putString("invoice_extra_note", s.invoiceExtraNote)
            .apply()
        FormatUtils.applyFromSettings(s)
        touchAppClock()
    }

    /** آخرین تغییر در برنامه با ساعت همگام‌شده */
    fun touchAppClock() {
        prefs.edit().putLong("last_change_at", TimeHelper.stamp()).apply()
    }

    fun getLastChangeAt(): Long = prefs.getLong("last_change_at", 0L)


    // ----- Customers -----
    fun getCustomers(): List<Customer> {
        val json = prefs.getString("customers", "[]") ?: "[]"
        val arr = JSONArray(json)
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Customer(
                id = o.getLong("id"),
                name = o.getString("name"),
                phone = o.optString("phone", ""),
                address = o.optString("address", ""),
                creditBalance = o.optDouble("creditBalance", 0.0),
                loyaltyPoints = o.optInt("loyaltyPoints", 0),
                notes = o.optString("notes", ""),
                createdAt = o.optLong("createdAt", 0L),
                creditLimit = o.optDouble("creditLimit", 0.0),
                loyaltyTier = o.optString("loyaltyTier", "NORMAL")
            )
        }.sortedBy { it.name }
    }

    fun getCustomer(id: Long): Customer? = getCustomers().find { it.id == id }

    fun addCustomer(name: String, phone: String = "", address: String = ""): Customer {
        val list = getCustomers().toMutableList()
        val c = Customer(next(), name.trim(), phone.trim(), address.trim(), 0.0, 0, "", System.currentTimeMillis())
        list.add(c)
        saveCustomers(list)
        return c
    }

    fun updateCustomer(c: Customer) {
        val list = getCustomers().toMutableList()
        val idx = list.indexOfFirst { it.id == c.id }
        if (idx >= 0) {
            list[idx] = c
            saveCustomers(list)
        }
    }

    private fun saveCustomers(list: List<Customer>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id)
                put("name", it.name)
                put("phone", it.phone)
                put("address", it.address)
                put("creditBalance", it.creditBalance)
                put("loyaltyPoints", it.loyaltyPoints)
                put("notes", it.notes)
                put("createdAt", it.createdAt)
                put("creditLimit", it.creditLimit)
                put("loyaltyTier", it.loyaltyTier)
            })
        }
        prefs.edit().putString("customers", arr.toString()).apply()
    }

    // ----- Sales -----
    fun getSales(): List<Sale> {
        val json = prefs.getString("sales", "[]") ?: "[]"
        val arr = JSONArray(json)
        return (0 until arr.length()).map { parseSale(arr.getJSONObject(it)) }
            .sortedByDescending { it.timestamp }
    }

    fun getSale(id: Long): Sale? = getSales().find { it.id == id }

    private fun parseSale(o: JSONObject): Sale {
        val itemsArr = o.getJSONArray("items")
        val items = (0 until itemsArr.length()).map { i ->
            val it = itemsArr.getJSONObject(i)
            SaleItem(
                productId = it.getLong("productId"),
                variantId = if (it.isNull("variantId")) null else it.getLong("variantId"),
                productName = it.getString("productName"),
                variantName = it.optString("variantName", ""),
                unit = it.optString("unit", "عدد"),
                quantity = it.getDouble("quantity"),
                unitPrice = it.getDouble("unitPrice"),
                discountPercent = it.optDouble("discountPercent", 0.0),
                lineTotal = it.getDouble("lineTotal")
            )
        }
        return Sale(
            id = o.getLong("id"),
            invoiceNumber = o.getString("invoiceNumber"),
            customerId = if (o.isNull("customerId")) null else o.getLong("customerId"),
            customerName = o.optString("customerName", "مهمان"),
            items = items,
            subtotal = o.getDouble("subtotal"),
            discountAmount = o.getDouble("discountAmount"),
            discountPercent = o.optDouble("discountPercent", 0.0),
            vatPercent = o.getDouble("vatPercent"),
            vatAmount = o.getDouble("vatAmount"),
            total = o.getDouble("total"),
            paidAmount = o.getDouble("paidAmount"),
            isCredit = o.getBoolean("isCredit"),
            paymentMethod = o.optString("paymentMethod", "CASH"),
            note = o.optString("note", ""),
            warehouseId = o.getLong("warehouseId"),
            timestamp = o.getLong("timestamp"),
            status = o.optString("status", "COMPLETED"),
            cashPaid = o.optDouble("cashPaid", 0.0),
            cardPaid = o.optDouble("cardPaid", 0.0),
            creditPaid = o.optDouble("creditPaid", 0.0),
            originalSaleId = o.optLong("originalSaleId", 0L),
            cashierName = o.optString("cashierName", "")
        )
    }

    private fun nextInvoiceNumber(): String {
        val n = prefs.getInt("invoice_seq", 1000) + 1
        prefs.edit().putInt("invoice_seq", n).apply()
        return "INV-$n"
    }

    /**
     * ثبت فروش: کاهش موجودی + ثبت فاکتور + در صورت نسیه افزایش بدهی مشتری
     */
    fun completeSale(
        cart: List<CartItem>,
        warehouseId: Long,
        customerId: Long?,
        discountPercent: Double,
        vatPercent: Double,
        paidAmount: Double,
        isCredit: Boolean,
        paymentMethod: String,
        note: String = "",
        cashPaid: Double = -1.0,
        cardPaid: Double = 0.0,
        creditPaid: Double = -1.0,
        cashierName: String = ""
    ): Sale? {
        if (cart.isEmpty()) return null

        lastError = ""
        // سقف اعتبار مشتری
        if (isCredit && customerId != null) {
            val c = getCustomer(customerId)
            if (c != null && c.creditLimit > 0) {
                val subtotal0 = cart.sumOf { it.lineTotal }
                val disc0 = subtotal0 * (discountPercent / 100.0)
                val after0 = subtotal0 - disc0
                val vat0 = after0 * (vatPercent / 100.0)
                val total0 = after0 + vat0
                val newDebt = c.creditBalance + (total0 - paidAmount).coerceAtLeast(0.0)
                if (newDebt > c.creditLimit + 0.01) {
                    lastError = "سقف اعتبار «${c.name}» (${FormatUtils.money(c.creditLimit)}) پر شده است"
                    return null
                }
            }
        }
        // بررسی موجودی: دستی = بدون کسر؛ بدون رکورد انبار = فروش آزاد؛ با رکورد = کنترل دقیق
        for (item in cart) {
            if (item.productId <= 0L) continue
            if (item.quantity <= 0) {
                lastError = "تعداد «${item.productName}» نامعتبر است"
                return null
            }
            if (item.unitPrice < 0) {
                lastError = "قیمت «${item.productName}» نامعتبر است"
                return null
            }
            val stocks = getAllStock().filter {
                it.productId == item.productId &&
                        it.warehouseId == warehouseId &&
                        it.variantId == item.variantId
            }
            if (stocks.isEmpty()) continue // بدون ردیابی موجودی
            val available = stocks.sumOf { it.quantity }
            if (available + 0.0001 < item.quantity) {
                lastError = "موجودی «${item.productName}» کافی نیست (موجود: ${FormatUtils.qty(available)}، نیاز: ${FormatUtils.qty(item.quantity)})"
                return null
            }
        }
        for (item in cart) {
            if (item.productId <= 0L) continue
            val stocks = getAllStock().filter {
                it.productId == item.productId &&
                        it.warehouseId == warehouseId &&
                        it.variantId == item.variantId
            }.sortedBy { it.expiryDate.takeIf { d -> d > 0 } ?: Long.MAX_VALUE }
            if (stocks.isEmpty()) continue
            var remaining = item.quantity
            for (s in stocks) {
                if (remaining <= 0) break
                val take = minOf(s.quantity, remaining)
                if (take > 0) {
                    exit(item.productId, warehouseId, take, item.variantId, s.batchNumber, "فروش")
                    remaining -= take
                }
            }
            if (remaining > 0.001) {
                lastError = "خطا در کسر موجودی «${item.productName}»"
                return null
            }
        }

        val saleItems = cart.map {
            SaleItem(
                productId = it.productId,
                variantId = it.variantId,
                productName = it.productName,
                variantName = it.variantName,
                unit = it.unit,
                quantity = it.quantity,
                unitPrice = it.unitPrice,
                discountPercent = it.discountPercent,
                lineTotal = it.lineTotal
            )
        }

        val subtotal = saleItems.sumOf { it.lineTotal }
        val discountAmount = subtotal * (discountPercent / 100.0)
        val afterDiscount = subtotal - discountAmount
        val vatAmount = afterDiscount * (vatPercent / 100.0)
        val total = afterDiscount + vatAmount

        val customerName = customerId?.let { getCustomer(it)?.name } ?: "مهمان"

        val cPaid = if (cashPaid < 0) (if (isCredit) 0.0 else paidAmount) else cashPaid
        val crPaid = if (creditPaid < 0) (if (isCredit) (total - paidAmount).coerceAtLeast(0.0) else 0.0) else creditPaid
        val sale = Sale(
            id = next(),
            invoiceNumber = nextInvoiceNumber(),
            customerId = customerId,
            customerName = customerName,
            items = saleItems,
            subtotal = subtotal,
            discountAmount = discountAmount,
            discountPercent = discountPercent,
            vatPercent = vatPercent,
            vatAmount = vatAmount,
            total = total,
            paidAmount = paidAmount,
            isCredit = isCredit,
            paymentMethod = paymentMethod,
            note = note,
            warehouseId = warehouseId,
            timestamp = TimeHelper.stamp(),
            cashPaid = cPaid,
            cardPaid = cardPaid,
            creditPaid = crPaid,
            cashierName = cashierName
        )

        // ذخیره فروش
        val sales = getSales().toMutableList()
        sales.add(0, sale)
        saveSales(sales)

        // اگر نسیه باشد، بدهی مشتری را افزایش بده
        if (isCredit && customerId != null) {
            val debt = total - paidAmount
            if (debt > 0) {
                getCustomer(customerId)?.let { c ->
                    updateCustomer(c.copy(creditBalance = c.creditBalance + debt))
                }
            }
        }

        // باشگاه مشتریان: اعطای امتیاز
        if (customerId != null) {
            awardLoyaltyPoints(customerId, total)
        }

        logActivity("SALE", sale.invoiceNumber + " = " + total)
        return sale
    }

    private fun saveSales(list: List<Sale>) {
        val arr = JSONArray()
        list.forEach { s ->
            val itemsArr = JSONArray()
            s.items.forEach { it ->
                itemsArr.put(JSONObject().apply {
                    put("productId", it.productId)
                    if (it.variantId != null) put("variantId", it.variantId) else put("variantId", JSONObject.NULL)
                    put("productName", it.productName)
                    put("variantName", it.variantName)
                    put("unit", it.unit)
                    put("quantity", it.quantity)
                    put("unitPrice", it.unitPrice)
                    put("discountPercent", it.discountPercent)
                    put("lineTotal", it.lineTotal)
                })
            }
            arr.put(JSONObject().apply {
                put("id", s.id)
                put("invoiceNumber", s.invoiceNumber)
                if (s.customerId != null) put("customerId", s.customerId) else put("customerId", JSONObject.NULL)
                put("customerName", s.customerName)
                put("items", itemsArr)
                put("subtotal", s.subtotal)
                put("discountAmount", s.discountAmount)
                put("discountPercent", s.discountPercent)
                put("vatPercent", s.vatPercent)
                put("vatAmount", s.vatAmount)
                put("total", s.total)
                put("paidAmount", s.paidAmount)
                put("isCredit", s.isCredit)
                put("paymentMethod", s.paymentMethod)
                put("note", s.note)
                put("warehouseId", s.warehouseId)
                put("timestamp", s.timestamp)
                put("status", s.status)
                put("cashPaid", s.cashPaid)
                put("cardPaid", s.cardPaid)
                put("creditPaid", s.creditPaid)
                put("originalSaleId", s.originalSaleId)
                put("cashierName", s.cashierName)
            })
        }
        prefs.edit().putString("sales", arr.toString()).apply()
    }

    /** محصولات قابل فروش با قیمت (از آخرین unitCost یا ۰) */
    fun getSellableProducts(
        warehouseId: Long,
        priceTier: String = "RETAIL"
    ): List<Triple<Product, ProductVariant?, Double>> {
        val result = mutableListOf<Triple<Product, ProductVariant?, Double>>()
        val stock = getAllStock().filter { it.warehouseId == warehouseId && it.quantity > 0 }
        val products = getProducts().associateBy { it.id }
        val variants = getVariants().associateBy { it.id }

        val grouped = stock.groupBy { it.productId to it.variantId }
        for ((key, items) in grouped) {
            val (pid, vid) = key
            val p = products[pid] ?: continue
            val v = vid?.let { variants[it] }
            val qty = items.sumOf { it.quantity }
            // اولویت با سطح قیمت تعریف‌شده؛ وگرنه میانگین هزینه
            val tierPrice = priceFor(pid, priceTier)
            val costAvg = items.map { it.unitCost }.filter { it > 0 }.average().takeIf { !it.isNaN() } ?: 0.0
            val price = when {
                tierPrice > 0 -> tierPrice
                p.salePrice > 0 -> p.salePrice
                costAvg > 0 -> costAvg
                else -> 0.0
            }
            if (qty > 0) result.add(Triple(p, v, price))
        }
        // کالاهای دارای قیمت فروش بدون موجودی (خدمات / فروش آزاد)
        val listedIds = result.map { it.first.id to it.second?.id }.toSet()
        for (p in products.values) {
            if (p.salePrice <= 0) continue
            val key = p.id to null as Long?
            if (key in listedIds) continue
            if (result.any { it.first.id == p.id && it.second == null }) continue
            val hasAnyStock = stock.any { it.productId == p.id }
            if (!hasAnyStock) {
                result.add(Triple(p, null, priceFor(p.id, priceTier).takeIf { it > 0 } ?: p.salePrice))
            }
        }
        return result.sortedBy { it.first.name }
    }

    /** موجودی قابل‌نمایش برای یک کالا در انبار */
    fun availableQty(productId: Long, warehouseId: Long, variantId: Long? = null): Double {
        return getAllStock().filter {
            it.productId == productId && it.warehouseId == warehouseId && it.variantId == variantId
        }.sumOf { it.quantity }
    }

    // ==================== بخش ۳: CRM و باشگاه مشتریان ====================

    /** امتیاز به ازای هر ۱۰٬۰۰۰ تومان خرید = ۱ امتیاز (قابل تنظیم) */
    fun getPointsPerAmount(): Double = prefs.getFloat("points_per_amount", 10000f).toDouble()

    fun setPointsPerAmount(v: Double) {
        prefs.edit().putFloat("points_per_amount", v.toFloat()).apply()
    }

    /** هر ۱۰۰ امتیاز = ۱٪ تخفیف (قابل تنظیم) */
    fun getPointsToDiscountRate(): Int = prefs.getInt("points_to_discount", 100)

    fun isSmsEnabled(): Boolean = prefs.getBoolean("sms_enabled", false)

    fun setSmsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("sms_enabled", enabled).apply()
    }

    fun getCustomerSales(customerId: Long): List<Sale> {
        return getSales().filter { it.customerId == customerId }
    }

    fun awardLoyaltyPoints(customerId: Long, saleTotal: Double) {
        val rate = getPointsPerAmount()
        if (rate <= 0) return
        val points = (saleTotal / rate).toInt()
        if (points <= 0) return
        getCustomer(customerId)?.let { c ->
            updateCustomer(c.copy(loyaltyPoints = c.loyaltyPoints + points))
        }
    }

    /** تبدیل امتیاز به درصد تخفیف؛ امتیاز مصرف می‌شود */
    fun redeemPoints(customerId: Long, pointsToUse: Int): Double {
        val c = getCustomer(customerId) ?: return 0.0
        if (pointsToUse <= 0 || c.loyaltyPoints < pointsToUse) return 0.0
        val rate = getPointsToDiscountRate()
        val discountPercent = (pointsToUse.toDouble() / rate)
        updateCustomer(c.copy(loyaltyPoints = c.loyaltyPoints - pointsToUse))
        return discountPercent.coerceAtMost(50.0) // سقف ۵۰٪
    }

    fun receivePayment(customerId: Long, amount: Double): Boolean {
        if (amount <= 0) return false
        val c = getCustomer(customerId) ?: return false
        val newBalance = (c.creditBalance - amount).coerceAtLeast(0.0)
        updateCustomer(c.copy(creditBalance = newBalance))
        return true
    }

    fun deleteCustomer(id: Long) {
        saveCustomers(getCustomers().filter { it.id != id })
    }

    fun getCustomersWithDebt(): List<Customer> =
        getCustomers().filter { it.creditBalance > 0 }.sortedByDescending { it.creditBalance }

    // ==================== بخش ۴: تامین‌کنندگان و خرید ====================

    fun getSuppliers(): List<Supplier> {
        val json = prefs.getString("suppliers", "[]") ?: "[]"
        val arr = JSONArray(json)
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Supplier(
                id = o.getLong("id"),
                name = o.getString("name"),
                phone = o.optString("phone", ""),
                address = o.optString("address", ""),
                payableBalance = o.optDouble("payableBalance", 0.0),
                notes = o.optString("notes", ""),
                createdAt = o.optLong("createdAt", 0L)
            )
        }.sortedBy { it.name }
    }

    fun getSupplier(id: Long): Supplier? = getSuppliers().find { it.id == id }

    fun addSupplier(name: String, phone: String = "", address: String = ""): Supplier {
        val list = getSuppliers().toMutableList()
        val s = Supplier(next(), name.trim(), phone.trim(), address.trim())
        list.add(s)
        saveSuppliers(list)
        return s
    }

    fun updateSupplier(s: Supplier) {
        val list = getSuppliers().toMutableList()
        val idx = list.indexOfFirst { it.id == s.id }
        if (idx >= 0) {
            list[idx] = s
            saveSuppliers(list)
        }
    }

    fun deleteSupplier(id: Long) {
        saveSuppliers(getSuppliers().filter { it.id != id })
    }

    private fun saveSuppliers(list: List<Supplier>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id)
                put("name", it.name)
                put("phone", it.phone)
                put("address", it.address)
                put("payableBalance", it.payableBalance)
                put("notes", it.notes)
                put("createdAt", it.createdAt)
            })
        }
        prefs.edit().putString("suppliers", arr.toString()).apply()
    }

    fun getPurchases(): List<Purchase> {
        val json = prefs.getString("purchases", "[]") ?: "[]"
        val arr = JSONArray(json)
        return (0 until arr.length()).map { parsePurchase(arr.getJSONObject(it)) }
            .sortedByDescending { it.timestamp }
    }

    fun getSupplierPurchases(supplierId: Long): List<Purchase> =
        getPurchases().filter { it.supplierId == supplierId }

    private fun parsePurchase(o: JSONObject): Purchase {
        val itemsArr = o.getJSONArray("items")
        val items = (0 until itemsArr.length()).map { i ->
            val it = itemsArr.getJSONObject(i)
            PurchaseItem(
                productId = it.getLong("productId"),
                variantId = if (it.isNull("variantId")) null else it.getLong("variantId"),
                productName = it.getString("productName"),
                quantity = it.getDouble("quantity"),
                unitCost = it.getDouble("unitCost"),
                lineTotal = it.getDouble("lineTotal")
            )
        }
        return Purchase(
            id = o.getLong("id"),
            invoiceNumber = o.getString("invoiceNumber"),
            supplierId = o.getLong("supplierId"),
            supplierName = o.getString("supplierName"),
            items = items,
            subtotal = o.getDouble("subtotal"),
            paidAmount = o.getDouble("paidAmount"),
            isCredit = o.getBoolean("isCredit"),
            warehouseId = o.getLong("warehouseId"),
            note = o.optString("note", ""),
            timestamp = o.getLong("timestamp"),
            status = o.optString("status", "COMPLETED")
        )
    }

    private fun nextPurchaseInvoice(): String {
        val n = prefs.getInt("purchase_seq", 500) + 1
        prefs.edit().putInt("purchase_seq", n).apply()
        return "PUR-$n"
    }

    /**
     * ثبت فاکتور خرید: افزایش موجودی + در صورت نسیه افزایش بدهی به تامین‌کننده
     */
    fun completePurchase(
        supplierId: Long,
        warehouseId: Long,
        items: List<PurchaseItem>,
        paidAmount: Double,
        isCredit: Boolean,
        note: String = ""
    ): Purchase? {
        val supplier = getSupplier(supplierId) ?: return null

        // افزایش موجودی با unitCost (قلم اختیاری)
        for (item in items) {
            // entry با unitCost ذخیره در stock
            val all = getAllStock().toMutableList()
            val existing = findStock(item.productId, warehouseId, item.variantId, "")
            if (existing != null) {
                val newQty = existing.quantity + item.quantity
                val avgCost = if (newQty > 0) {
                    ((existing.quantity * existing.unitCost) + (item.quantity * item.unitCost)) / newQty
                } else item.unitCost
                val idx = all.indexOfFirst { it.id == existing.id }
                all[idx] = existing.copy(quantity = newQty, unitCost = avgCost)
                saveStock(all)
            } else {
                entry(item.productId, warehouseId, item.quantity, item.variantId, "", 0L, "خرید از ${supplier.name}")
                // set unit cost
                val stocks = getAllStock().toMutableList()
                val s = stocks.find {
                    it.productId == item.productId && it.warehouseId == warehouseId &&
                            it.variantId == item.variantId
                }
                if (s != null) {
                    val idx = stocks.indexOfFirst { it.id == s.id }
                    stocks[idx] = s.copy(unitCost = item.unitCost)
                    saveStock(stocks)
                }
            }
        }

        val subtotal = items.sumOf { it.lineTotal }
        val purchase = Purchase(
            id = next(),
            invoiceNumber = nextPurchaseInvoice(),
            supplierId = supplierId,
            supplierName = supplier.name,
            items = items,
            subtotal = subtotal,
            paidAmount = paidAmount,
            isCredit = isCredit,
            warehouseId = warehouseId,
            note = note,
            timestamp = System.currentTimeMillis()
        )

        val list = getPurchases().toMutableList()
        list.add(0, purchase)
        savePurchases(list)

        // بدهی به تامین‌کننده
        if (isCredit) {
            val debt = subtotal - paidAmount
            if (debt > 0) {
                updateSupplier(supplier.copy(payableBalance = supplier.payableBalance + debt))
            }
        }

        logActivity("PURCHASE", purchase.invoiceNumber + " = " + subtotal)
        return purchase
    }

    private fun savePurchases(list: List<Purchase>) {
        val arr = JSONArray()
        list.forEach { p ->
            val itemsArr = JSONArray()
            p.items.forEach { it ->
                itemsArr.put(JSONObject().apply {
                    put("productId", it.productId)
                    if (it.variantId != null) put("variantId", it.variantId) else put("variantId", JSONObject.NULL)
                    put("productName", it.productName)
                    put("quantity", it.quantity)
                    put("unitCost", it.unitCost)
                    put("lineTotal", it.lineTotal)
                })
            }
            arr.put(JSONObject().apply {
                put("id", p.id)
                put("invoiceNumber", p.invoiceNumber)
                put("supplierId", p.supplierId)
                put("supplierName", p.supplierName)
                put("items", itemsArr)
                put("subtotal", p.subtotal)
                put("paidAmount", p.paidAmount)
                put("isCredit", p.isCredit)
                put("warehouseId", p.warehouseId)
                put("note", p.note)
                put("timestamp", p.timestamp)
                put("status", p.status)
            })
        }
        prefs.edit().putString("purchases", arr.toString()).apply()
    }

    fun paySupplier(supplierId: Long, amount: Double): Boolean {
        if (amount <= 0) return false
        val s = getSupplier(supplierId) ?: return false
        val newBal = (s.payableBalance - amount).coerceAtLeast(0.0)
        updateSupplier(s.copy(payableBalance = newBal))
        return true
    }

    fun getSuppliersWithPayable(): List<Supplier> =
        getSuppliers().filter { it.payableBalance > 0 }.sortedByDescending { it.payableBalance }

    // ==================== بخش ۵: تحلیل‌های هوشمند ====================

    data class DailyKpi(
        val todaySalesTotal: Double,
        val todaySalesCount: Int,
        val todayProfit: Double,
        val monthSalesTotal: Double,
        val lowStockCount: Int,
        val expiringCount: Int,
        val totalReceivables: Double, // طلب از مشتریان
        val totalPayables: Double     // بدهی به تامین‌کنندگان
    )

    data class TopProduct(
        val productName: String,
        val quantitySold: Double,
        val revenue: Double
    )

    data class ForecastItem(
        val productId: Long,
        val productName: String,
        val currentStock: Double,
        val unit: String,
        val avgDailySales: Double,
        val daysUntilEmpty: Int // -1 = فروش نداشته
    )

    data class PLReport(
        val revenue: Double,
        val cogs: Double,        // بهای تمام‌شده کالای فروش‌رفته
        val grossProfit: Double,
        val purchaseCost: Double, // کل خریدها در بازه
        val netProfit: Double
    )

    data class BasketPair(
        val productA: String,
        val productB: String,
        val count: Int
    )

    private fun startOfToday(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun startOfMonth(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun getDailyKpi(): DailyKpi {
        val todayStart = startOfToday()
        val monthStart = startOfMonth()
        val sales = getSales().filter { it.status == "COMPLETED" }
        val todaySales = sales.filter { it.timestamp >= todayStart }
        val monthSales = sales.filter { it.timestamp >= monthStart }

        val todayTotal = todaySales.sumOf { it.total }
        val todayProfit = todaySales.sumOf { sale ->
            sale.items.sumOf { item ->
                val cost = getAllStock().filter { it.productId == item.productId }
                    .map { it.unitCost }.firstOrNull() ?: 0.0
                item.lineTotal - (item.quantity * cost)
            } - sale.discountAmount
        }

        return DailyKpi(
            todaySalesTotal = todayTotal,
            todaySalesCount = todaySales.size,
            todayProfit = todayProfit,
            monthSalesTotal = monthSales.sumOf { it.total },
            lowStockCount = getInventoryRows().count { it.quantity <= 5 },
            expiringCount = getExpiringItems().size,
            totalReceivables = getCustomers().sumOf { it.creditBalance },
            totalPayables = getSuppliers().sumOf { it.payableBalance }
        )
    }

    fun getTopProducts(limit: Int = 10, days: Int = 30): List<TopProduct> {
        val since = System.currentTimeMillis() - days * 24L * 60 * 60 * 1000
        val map = mutableMapOf<String, Pair<Double, Double>>() // name -> qty, revenue
        getSales().filter { it.timestamp >= since && it.status == "COMPLETED" }.forEach { sale ->
            sale.items.forEach { item ->
                val key = item.productName + if (item.variantName.isNotBlank()) " (${item.variantName})" else ""
                val prev = map[key] ?: (0.0 to 0.0)
                map[key] = (prev.first + item.quantity) to (prev.second + item.lineTotal)
            }
        }
        return map.map { (name, v) -> TopProduct(name, v.first, v.second) }
            .sortedByDescending { it.revenue }
            .take(limit)
    }

    fun getStockForecast(daysLookback: Int = 30): List<ForecastItem> {
        val since = System.currentTimeMillis() - daysLookback * 24L * 60 * 60 * 1000
        val salesQty = mutableMapOf<Long, Double>()
        getSales().filter { it.timestamp >= since && it.status == "COMPLETED" }.forEach { sale ->
            sale.items.forEach { item ->
                salesQty[item.productId] = (salesQty[item.productId] ?: 0.0) + item.quantity
            }
        }
        val products = getProducts().associateBy { it.id }
        val stockByProduct = getAllStock().groupBy { it.productId }
            .mapValues { it.value.sumOf { s -> s.quantity } }

        return products.values.map { p ->
            val stock = stockByProduct[p.id] ?: 0.0
            val sold = salesQty[p.id] ?: 0.0
            val avgDaily = sold / daysLookback.coerceAtLeast(1)
            val daysLeft = if (avgDaily > 0.001) (stock / avgDaily).toInt() else -1
            ForecastItem(p.id, p.name, stock, p.unit, avgDaily, daysLeft)
        }.filter { it.avgDailySales > 0 || it.currentStock > 0 }
            .sortedBy { if (it.daysUntilEmpty < 0) Int.MAX_VALUE else it.daysUntilEmpty }
    }

    fun getPLReport(days: Int = 30): PLReport {
        val since = System.currentTimeMillis() - days * 24L * 60 * 60 * 1000
        val sales = getSales().filter { it.timestamp >= since && it.status == "COMPLETED" }
        val revenue = sales.sumOf { it.total }

        // COGS تقریبی از unitCost
        var cogs = 0.0
        sales.forEach { sale ->
            sale.items.forEach { item ->
                val cost = getAllStock().filter { it.productId == item.productId }
                    .map { it.unitCost }.filter { it > 0 }.average().takeIf { !it.isNaN() } ?: 0.0
                cogs += item.quantity * cost
            }
        }

        val purchases = getPurchases().filter { it.timestamp >= since }
        val purchaseCost = purchases.sumOf { it.subtotal }
        val gross = revenue - cogs
        return PLReport(revenue, cogs, gross, purchaseCost, gross)
    }

    fun getBasketAnalysis(limit: Int = 10): List<BasketPair> {
        val pairCount = mutableMapOf<String, Int>()
        getSales().filter { it.status == "COMPLETED" && it.items.size >= 2 }.forEach { sale ->
            val names = sale.items.map { it.productName }.distinct().sorted()
            for (i in names.indices) {
                for (j in i + 1 until names.size) {
                    val key = "${names[i]}|||${names[j]}"
                    pairCount[key] = (pairCount[key] ?: 0) + 1
                }
            }
        }
        return pairCount.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map {
                val parts = it.key.split("|||")
                BasketPair(parts[0], parts[1], it.value)
            }
    }

    // ==================== بخش ۶: چندکاربره، شعبه، بکاپ ====================

    fun getUsers(): List<AppUser> {
        val json = prefs.getString("users", null)
        if (json == null) {
            // کاربر پیش‌فرض مدیر با PIN 0000
            val admin = AppUser(next(), "admin", "مدیر", hashPin("0000"), "ADMIN")
            saveUsers(listOf(admin))
            return listOf(admin)
        }
        val arr = JSONArray(json)
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            AppUser(
                id = o.getLong("id"),
                username = o.getString("username"),
                displayName = o.getString("displayName"),
                pinHash = o.getString("pinHash"),
                role = o.getString("role"),
                branchId = if (o.isNull("branchId")) null else o.getLong("branchId"),
                isActive = o.optBoolean("isActive", true)
            )
        }
    }

    fun addUser(username: String, displayName: String, pin: String, role: String, branchId: Long? = null): AppUser {
        val list = getUsers().toMutableList()
        val u = AppUser(next(), username.trim(), displayName.trim(), hashPin(pin), role, branchId)
        list.add(u)
        saveUsers(list)
        return u
    }

    fun updateUser(u: AppUser) {
        val list = getUsers().toMutableList()
        val idx = list.indexOfFirst { it.id == u.id }
        if (idx >= 0) {
            list[idx] = u
            saveUsers(list)
        }
    }

    fun deleteUser(id: Long) {
        // حداقل یک ADMIN باید بماند
        val remaining = getUsers().filter { it.id != id }
        if (remaining.none { it.role == "ADMIN" }) return
        saveUsers(remaining)
    }

    fun authenticate(username: String, pin: String): AppUser? {
        return getUsers().find {
            it.isActive && it.username.equals(username, true) && it.pinHash == hashPin(pin)
        }
    }

    fun hashPin(pin: String): String {
        // هش ساده برای PIN کوتاه (نه برای امنیت بانکی)
        return pin.toByteArray().fold(0) { acc, b -> (acc * 31 + b) }.toString()
    }

    private fun saveUsers(list: List<AppUser>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id)
                put("username", it.username)
                put("displayName", it.displayName)
                put("pinHash", it.pinHash)
                put("role", it.role)
                if (it.branchId != null) put("branchId", it.branchId) else put("branchId", JSONObject.NULL)
                put("isActive", it.isActive)
            })
        }
        prefs.edit().putString("users", arr.toString()).apply()
    }

    // ----- Branches -----
    fun getBranches(): List<Branch> {
        val json = prefs.getString("branches", null)
        if (json == null) {
            val wh = getDefaultWarehouse()
            val b = Branch(next(), "شعبه اصلی", wh.id)
            saveBranches(listOf(b))
            return listOf(b)
        }
        val arr = JSONArray(json)
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Branch(
                id = o.getLong("id"),
                name = o.getString("name"),
                warehouseId = o.getLong("warehouseId"),
                isActive = o.optBoolean("isActive", true)
            )
        }
    }

    fun addBranch(name: String, warehouseId: Long): Branch {
        val list = getBranches().toMutableList()
        val b = Branch(next(), name.trim(), warehouseId)
        list.add(b)
        saveBranches(list)
        return b
    }

    private fun saveBranches(list: List<Branch>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id)
                put("name", it.name)
                put("warehouseId", it.warehouseId)
                put("isActive", it.isActive)
            })
        }
        prefs.edit().putString("branches", arr.toString()).apply()
    }

    // ----- Current session -----
    fun setCurrentUserId(id: Long) {
        prefs.edit().putLong("current_user_id", id).apply()
    }

    fun getCurrentUser(): AppUser? {
        val id = prefs.getLong("current_user_id", -1L)
        if (id < 0) return null
        return getUsers().find { it.id == id }
    }

    fun clearCurrentUser() {
        prefs.edit().remove("current_user_id").apply()
    }

    fun isLoginRequired(): Boolean = prefs.getBoolean("login_required", false)

    fun setLoginRequired(v: Boolean) {
        prefs.edit().putBoolean("login_required", v).apply()
    }

    // ----- Backup / Restore (JSON کامل برای اشتراک در گوگل درایو و...) -----

    fun exportBackupJson(): String {
        val root = JSONObject()
        root.put("version", 6)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("shopName", getSettings().shopName)

        val keys = listOf(
            "products", "variants", "stock", "warehouses", "transactions", "transfers",
            "customers", "sales", "suppliers", "purchases", "users", "branches", "colleagues", "handovers", "cash_shifts", "product_prices", "stock_takes", "bank_checks", "expenses", "proformas", "visit_orders", "bundles"
        )
        keys.forEach { key ->
            root.put(key, prefs.getString(key, "[]") ?: "[]")
        }
        root.put("settings", JSONObject().apply {
            put("shop_name", prefs.getString("shop_name", "فروشگاه من"))
            put("vat_percent", prefs.getFloat("vat_percent", 9f).toDouble())
            put("currency", prefs.getString("currency", "تومان"))
            put("points_per_amount", prefs.getFloat("points_per_amount", 10000f).toDouble())
            put("sms_enabled", prefs.getBoolean("sms_enabled", false))
            put("login_required", prefs.getBoolean("login_required", false))
        })
        root.put("next_id", nextId)
        root.put("invoice_seq", prefs.getInt("invoice_seq", 1000))
        root.put("purchase_seq", prefs.getInt("purchase_seq", 500))
        return root.toString(2)
    }

    fun importBackupJson(json: String): Boolean {
        return try {
            val root = JSONObject(json)
            val keys = listOf(
                "products", "variants", "stock", "warehouses", "transactions", "transfers",
                "customers", "sales", "suppliers", "purchases", "users", "branches", "colleagues", "handovers", "cash_shifts", "product_prices", "stock_takes", "bank_checks", "expenses", "proformas", "visit_orders", "bundles"
            )
            val editor = prefs.edit()
            keys.forEach { key ->
                if (root.has(key)) {
                    val v = root.get(key)
                    editor.putString(key, if (v is String) v else v.toString())
                }
            }
            if (root.has("settings")) {
                val s = root.getJSONObject("settings")
                editor.putString("shop_name", s.optString("shop_name", "فروشگاه من"))
                editor.putFloat("vat_percent", s.optDouble("vat_percent", 9.0).toFloat())
                editor.putString("currency", s.optString("currency", "تومان"))
                editor.putFloat("points_per_amount", s.optDouble("points_per_amount", 10000.0).toFloat())
                editor.putBoolean("sms_enabled", s.optBoolean("sms_enabled", false))
                editor.putBoolean("login_required", s.optBoolean("login_required", false))
            }
            if (root.has("next_id")) editor.putLong("next_id", root.getLong("next_id"))
            if (root.has("invoice_seq")) editor.putInt("invoice_seq", root.getInt("invoice_seq"))
            if (root.has("purchase_seq")) editor.putInt("purchase_seq", root.getInt("purchase_seq"))
            editor.apply()
            true
        } catch (e: Exception) {
            false
        }
    }

    // ==================== بخش ۷: امنیت و لاگ ====================

    fun logActivity(action: String, detail: String = "") {
        val user = getCurrentUser()
        val list = getActivityLogs().toMutableList()
        list.add(0, ActivityLog(
            id = next(),
            userId = user?.id,
            username = user?.displayName ?: user?.username ?: "سیستم",
            action = action,
            detail = detail,
            timestamp = System.currentTimeMillis()
        ))
        // نگه داشتن حداکثر ۵۰۰ لاگ
        val trimmed = list.take(500)
        saveActivityLogs(trimmed)
    }

    fun getActivityLogs(limit: Int = 100): List<ActivityLog> {
        val json = prefs.getString("activity_logs", "[]") ?: "[]"
        val arr = JSONArray(json)
        return (0 until minOf(arr.length(), limit)).map {
            val o = arr.getJSONObject(it)
            ActivityLog(
                id = o.getLong("id"),
                userId = if (o.isNull("userId")) null else o.getLong("userId"),
                username = o.getString("username"),
                action = o.getString("action"),
                detail = o.optString("detail", ""),
                timestamp = o.getLong("timestamp")
            )
        }
    }

    private fun saveActivityLogs(list: List<ActivityLog>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id)
                if (it.userId != null) put("userId", it.userId) else put("userId", JSONObject.NULL)
                put("username", it.username)
                put("action", it.action)
                put("detail", it.detail)
                put("timestamp", it.timestamp)
            })
        }
        prefs.edit().putString("activity_logs", arr.toString()).apply()
    }

    fun isBiometricEnabled(): Boolean = prefs.getBoolean("biometric_enabled", false)

    fun setBiometricEnabled(v: Boolean) {
        prefs.edit().putBoolean("biometric_enabled", v).apply()
    }

    fun isAutoBackupEnabled(): Boolean = prefs.getBoolean("auto_backup_enabled", false)

    fun setAutoBackupEnabled(v: Boolean) {
        prefs.edit().putBoolean("auto_backup_enabled", v).apply()
    }

    fun changeAdminPin(oldPin: String, newPin: String): Boolean {
        val admin = getUsers().find { it.role == "ADMIN" } ?: return false
        if (admin.pinHash != hashPin(oldPin)) return false
        if (newPin.length < 4) return false
        updateUser(admin.copy(pinHash = hashPin(newPin)))
        logActivity("CHANGE_PIN", "تغییر PIN مدیر")
        return true
    }

    /** مسیر آخرین بکاپ خودکار */
    fun setLastAutoBackupPath(path: String) {
        prefs.edit().putString("last_auto_backup", path).apply()
    }

    fun getLastAutoBackupPath(): String = prefs.getString("last_auto_backup", "") ?: ""

    // ==================== بخش ۱۰: وب‌هوک و ارز ====================

    fun getWebhookConfig(): WebhookConfig {
        return WebhookConfig(
            enabled = prefs.getBoolean("wh_enabled", false),
            url = prefs.getString("wh_url", "") ?: "",
            secret = prefs.getString("wh_secret", "") ?: "",
            onSale = prefs.getBoolean("wh_on_sale", true),
            onPurchase = prefs.getBoolean("wh_on_purchase", true),
            onStockChange = prefs.getBoolean("wh_on_stock", false)
        )
    }

    fun saveWebhookConfig(c: WebhookConfig) {
        prefs.edit()
            .putBoolean("wh_enabled", c.enabled)
            .putString("wh_url", c.url)
            .putString("wh_secret", c.secret)
            .putBoolean("wh_on_sale", c.onSale)
            .putBoolean("wh_on_purchase", c.onPurchase)
            .putBoolean("wh_on_stock", c.onStockChange)
            .apply()
    }

    fun getBaseCurrency(): String = prefs.getString("base_currency", "IRR") ?: "IRR"

    fun setBaseCurrency(code: String) {
        prefs.edit().putString("base_currency", code).apply()
    }

    fun getCurrencyRates(): List<CurrencyRate> {
        val json = prefs.getString("currency_rates", null)
        if (json == null) {
            val defaults = listOf(
                CurrencyRate("IRR", "تومان", 1.0),
                CurrencyRate("USD", "دلار", 600000.0),
                CurrencyRate("EUR", "یورو", 650000.0),
                CurrencyRate("AED", "درهم", 163000.0),
                CurrencyRate("TRY", "لیر", 18000.0)
            )
            saveCurrencyRates(defaults)
            return defaults
        }
        val arr = JSONArray(json)
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            CurrencyRate(o.getString("code"), o.getString("name"), o.getDouble("rateToBase"))
        }
    }

    fun saveCurrencyRates(list: List<CurrencyRate>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().apply {
                put("code", it.code)
                put("name", it.name)
                put("rateToBase", it.rateToBase)
            })
        }
        prefs.edit().putString("currency_rates", arr.toString()).apply()
    }

    fun convert(amountBase: Double, toCode: String): Double {
        val rate = getCurrencyRates().find { it.code == toCode }?.rateToBase ?: 1.0
        if (rate <= 0) return amountBase
        return amountBase / rate
    }

    fun catalogText(): String {
        val settings = getSettings()
        val sb = StringBuilder()
        sb.appendLine(settings.shopName)
        sb.appendLine("─".repeat(20))
        getProducts().forEach { p ->
            val stock = getAllStock().filter { it.productId == p.id }.sumOf { it.quantity }
            val price = getAllStock().filter { it.productId == p.id && it.unitCost > 0 }
                .map { it.unitCost }.average().takeIf { !it.isNaN() } ?: 0.0
            sb.appendLine("${p.name} | ${p.unit} | موجودی: ${stock.toLong()} | قیمت: ${price.toLong()}")
        }
        return sb.toString()
    }

    // ==================== بخش ۱۱: تحویل / بازتحویل همکار ====================

    fun getColleagues(): List<Colleague> {
        val json = prefs.getString("colleagues", "[]") ?: "[]"
        val arr = JSONArray(json)
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Colleague(
                id = o.getLong("id"),
                name = o.getString("name"),
                phone = o.optString("phone", ""),
                notes = o.optString("notes", ""),
                isActive = o.optBoolean("isActive", true),
                createdAt = o.optLong("createdAt", 0L)
            )
        }.sortedBy { it.name }
    }

    fun getColleague(id: Long): Colleague? = getColleagues().find { it.id == id }

    fun addColleague(name: String, phone: String = ""): Colleague {
        val list = getColleagues().toMutableList()
        val c = Colleague(next(), name.trim(), phone.trim())
        list.add(c)
        saveColleagues(list)
        return c
    }

    fun updateColleague(c: Colleague) {
        val list = getColleagues().toMutableList()
        val idx = list.indexOfFirst { it.id == c.id }
        if (idx >= 0) {
            list[idx] = c
            saveColleagues(list)
        }
    }

    private fun saveColleagues(list: List<Colleague>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id)
                put("name", it.name)
                put("phone", it.phone)
                put("notes", it.notes)
                put("isActive", it.isActive)
                put("createdAt", it.createdAt)
            })
        }
        prefs.edit().putString("colleagues", arr.toString()).apply()
    }

    fun getHandovers(): List<Handover> {
        val json = prefs.getString("handovers", "[]") ?: "[]"
        val arr = JSONArray(json)
        return (0 until arr.length()).map { parseHandover(arr.getJSONObject(it)) }
            .sortedByDescending { it.timestamp }
    }

    fun getColleagueHandovers(colleagueId: Long): List<Handover> =
        getHandovers().filter { it.colleagueId == colleagueId }

    private fun parseHandover(o: JSONObject): Handover {
        val itemsArr = o.getJSONArray("items")
        val items = (0 until itemsArr.length()).map { i ->
            val it = itemsArr.getJSONObject(i)
            HandoverItem(
                productId = it.getLong("productId"),
                variantId = if (it.isNull("variantId")) null else it.getLong("variantId"),
                productName = it.getString("productName"),
                quantity = it.getDouble("quantity"),
                unit = it.optString("unit", "عدد")
            )
        }
        return Handover(
            id = o.getLong("id"),
            type = o.getString("type"),
            colleagueId = o.getLong("colleagueId"),
            colleagueName = o.getString("colleagueName"),
            warehouseId = o.getLong("warehouseId"),
            items = items,
            note = o.optString("note", ""),
            timestamp = o.getLong("timestamp"),
            receiptNumber = o.getString("receiptNumber")
        )
    }

    private fun nextHandoverReceipt(type: String): String {
        val key = if (type == "HANDOVER") "handover_seq" else "return_seq"
        val prefix = if (type == "HANDOVER") "HD" else "RT"
        val n = prefs.getInt(key, 100) + 1
        prefs.edit().putInt(key, n).apply()
        return "$prefix-$n"
    }

    /**
     * تحویل به همکار: کاهش از انبار + ثبت
     */
    fun handoverToColleague(
        colleagueId: Long,
        warehouseId: Long,
        items: List<HandoverItem>,
        note: String = ""
    ): Handover? {
        if (items.isEmpty()) return null
        val colleague = getColleague(colleagueId) ?: return null

        for (item in items) {
            val ok = exit(item.productId, warehouseId, item.quantity, item.variantId, "", "تحویل به ${colleague.name}")
            if (!ok) return null
        }

        val h = Handover(
            id = next(),
            type = "HANDOVER",
            colleagueId = colleagueId,
            colleagueName = colleague.name,
            warehouseId = warehouseId,
            items = items,
            note = note,
            timestamp = System.currentTimeMillis(),
            receiptNumber = nextHandoverReceipt("HANDOVER")
        )
        val list = getHandovers().toMutableList()
        list.add(0, h)
        saveHandovers(list)
        logActivity("HANDOVER", h.receiptNumber + " → " + colleague.name)
        return h
    }

    /**
     * بازتحویل از همکار: افزایش به انبار + ثبت
     */
    fun returnFromColleague(
        colleagueId: Long,
        warehouseId: Long,
        items: List<HandoverItem>,
        note: String = ""
    ): Handover? {
        if (items.isEmpty()) return null
        val colleague = getColleague(colleagueId) ?: return null

        // بررسی مانده
        val balances = getColleagueBalances(colleagueId).associateBy { it.productId }
        for (item in items) {
            val bal = balances[item.productId]?.quantity ?: 0.0
            if (bal < item.quantity) return null
        }

        for (item in items) {
            entry(item.productId, warehouseId, item.quantity, item.variantId, "", 0L, "بازتحویل از ${colleague.name}")
        }

        val h = Handover(
            id = next(),
            type = "RETURN",
            colleagueId = colleagueId,
            colleagueName = colleague.name,
            warehouseId = warehouseId,
            items = items,
            note = note,
            timestamp = System.currentTimeMillis(),
            receiptNumber = nextHandoverReceipt("RETURN")
        )
        val list = getHandovers().toMutableList()
        list.add(0, h)
        saveHandovers(list)
        logActivity("RETURN", h.receiptNumber + " ← " + colleague.name)
        return h
    }

    /** مانده زنده نزد همکار = مجموع تحویل − مجموع بازتحویل */
    fun getColleagueBalances(colleagueId: Long): List<ColleagueBalance> {
        val map = mutableMapOf<Long, Pair<String, Double>>() // productId -> name, qty
        val units = getProducts().associate { it.id to it.unit }

        getColleagueHandovers(colleagueId).forEach { h ->
            h.items.forEach { item ->
                val prev = map[item.productId] ?: (item.productName to 0.0)
                val delta = if (h.type == "HANDOVER") item.quantity else -item.quantity
                map[item.productId] = prev.first to (prev.second + delta)
            }
        }
        return map.map { (pid, v) ->
            ColleagueBalance(pid, v.first, units[pid] ?: "عدد", v.second)
        }.filter { it.quantity > 0.001 }.sortedBy { it.productName }
    }

    private fun saveHandovers(list: List<Handover>) {
        val arr = JSONArray()
        list.forEach { h ->
            val itemsArr = JSONArray()
            h.items.forEach { it ->
                itemsArr.put(JSONObject().apply {
                    put("productId", it.productId)
                    if (it.variantId != null) put("variantId", it.variantId) else put("variantId", JSONObject.NULL)
                    put("productName", it.productName)
                    put("quantity", it.quantity)
                    put("unit", it.unit)
                })
            }
            arr.put(JSONObject().apply {
                put("id", h.id)
                put("type", h.type)
                put("colleagueId", h.colleagueId)
                put("colleagueName", h.colleagueName)
                put("warehouseId", h.warehouseId)
                put("items", itemsArr)
                put("note", h.note)
                put("timestamp", h.timestamp)
                put("receiptNumber", h.receiptNumber)
            })
        }
        prefs.edit().putString("handovers", arr.toString()).apply()
    }

    // ==================== نسخه تجاری ۳.۰ ====================

    private fun loadJsonArray(key: String): JSONArray {
        return JSONArray(prefs.getString(key, "[]") ?: "[]")
    }

    private fun saveJsonArray(key: String, arr: JSONArray) {
        prefs.edit().putString(key, arr.toString()).apply()
    }

    // ----- شیفت صندوق -----
    fun getShifts(): List<CashShift> {
        val arr = loadJsonArray("cash_shifts")
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            CashShift(
                id = o.getLong("id"),
                openedAt = o.getLong("openedAt"),
                closedAt = o.optLong("closedAt", 0L),
                openingCash = o.getDouble("openingCash"),
                closingCash = o.optDouble("closingCash", 0.0),
                expectedCash = o.optDouble("expectedCash", 0.0),
                variance = o.optDouble("variance", 0.0),
                openedBy = o.optString("openedBy", ""),
                note = o.optString("note", ""),
                isOpen = o.optBoolean("isOpen", true)
            )
        }.sortedByDescending { it.openedAt }
    }

    fun getOpenShift(): CashShift? = getShifts().find { it.isOpen }

    fun openShift(openingCash: Double, cashierName: String = ""): CashShift {
        val user = cashierName.ifBlank { getCurrentUser()?.displayName ?: "متصدی" }
        val s = CashShift(next(), TimeHelper.stamp(), openingCash = openingCash, openedBy = user)
        val list = getShifts().toMutableList()
        list.add(0, s)
        val arr = JSONArray()
        list.forEach { sh ->
            arr.put(JSONObject().apply {
                put("id", sh.id); put("openedAt", sh.openedAt); put("closedAt", sh.closedAt)
                put("openingCash", sh.openingCash); put("closingCash", sh.closingCash)
                put("expectedCash", sh.expectedCash); put("variance", sh.variance)
                put("openedBy", sh.openedBy); put("note", sh.note); put("isOpen", sh.isOpen)
            })
        }
        saveJsonArray("cash_shifts", arr)
        logActivity("SHIFT_OPEN", user)
        touchAppClock()
        return s
    }

    fun closeShift(closingCash: Double, note: String = ""): CashShift? {
        val open = getOpenShift() ?: return null
        val salesCash = getSales().filter {
            it.timestamp >= open.openedAt && it.status == "COMPLETED" && !it.isCredit
        }.sumOf { it.paidAmount.coerceAtLeast(it.total) }
        val expected = open.openingCash + salesCash
        val variance = closingCash - expected
        val updated = open.copy(
            closedAt = System.currentTimeMillis(),
            closingCash = closingCash,
            expectedCash = expected,
            variance = variance,
            note = note,
            isOpen = false
        )
        val list = getShifts().map { if (it.id == open.id) updated else it }
        val arr = JSONArray()
        list.forEach { s ->
            arr.put(JSONObject().apply {
                put("id", s.id); put("openedAt", s.openedAt); put("closedAt", s.closedAt)
                put("openingCash", s.openingCash); put("closingCash", s.closingCash)
                put("expectedCash", s.expectedCash); put("variance", s.variance)
                put("openedBy", s.openedBy); put("note", s.note); put("isOpen", s.isOpen)
            })
        }
        saveJsonArray("cash_shifts", arr)
        logActivity("SHIFT_CLOSE", "واریانس: $variance")
        return updated
    }

    // ----- قیمت چندسطحی -----
    fun getProductPrices(): Map<Long, ProductPrice> {
        val arr = loadJsonArray("product_prices")
        val map = mutableMapOf<Long, ProductPrice>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val pp = ProductPrice(
                productId = o.getLong("productId"),
                retail = o.optDouble("retail", 0.0),
                wholesale = o.optDouble("wholesale", 0.0),
                vip = o.optDouble("vip", 0.0),
                colleague = o.optDouble("colleague", 0.0)
            )
            map[pp.productId] = pp
        }
        return map
    }

    fun setProductPrice(pp: ProductPrice) {
        val map = getProductPrices().toMutableMap()
        map[pp.productId] = pp
        val arr = JSONArray()
        map.values.forEach {
            arr.put(JSONObject().apply {
                put("productId", it.productId)
                put("retail", it.retail); put("wholesale", it.wholesale)
                put("vip", it.vip); put("colleague", it.colleague)
            })
        }
        saveJsonArray("product_prices", arr)
    }

    fun priceFor(productId: Long, tier: String): Double {
        val pp = getProductPrices()[productId]
        val fromTier = when (tier.uppercase()) {
            "WHOLESALE" -> pp?.wholesale?.takeIf { it > 0 }
            "COLLEAGUE" -> pp?.colleague?.takeIf { it > 0 }
            "VIP" -> pp?.vip?.takeIf { it > 0 } ?: pp?.retail?.takeIf { it > 0 }
            else -> pp?.retail?.takeIf { it > 0 }
        }
        if (fromTier != null && fromTier > 0) return fromTier
        // قیمت ثبت‌شده روی خود کالا
        return getProduct(productId)?.salePrice?.takeIf { it > 0 } ?: 0.0
    }

    // ----- شمارش انبار -----
    fun getStockTakes(): List<StockTake> {
        val arr = loadJsonArray("stock_takes")
        return (0 until arr.length()).map { parseStockTake(arr.getJSONObject(it)) }
            .sortedByDescending { it.timestamp }
    }

    private fun parseStockTake(o: JSONObject): StockTake {
        val linesArr = o.getJSONArray("lines")
        val lines = (0 until linesArr.length()).map { i ->
            val l = linesArr.getJSONObject(i)
            StockTakeLine(
                productId = l.getLong("productId"),
                productName = l.getString("productName"),
                systemQty = l.getDouble("systemQty"),
                countedQty = l.getDouble("countedQty"),
                unit = l.optString("unit", "عدد")
            )
        }
        return StockTake(
            id = o.getLong("id"),
            warehouseId = o.getLong("warehouseId"),
            warehouseName = o.getString("warehouseName"),
            timestamp = o.getLong("timestamp"),
            note = o.optString("note", ""),
            lines = lines
        )
    }

    fun saveStockTake(warehouseId: Long, lines: List<StockTakeLine>, note: String, applyAdjust: Boolean): StockTake {
        val wh = getWarehouses().find { it.id == warehouseId }
        val st = StockTake(next(), warehouseId, wh?.name ?: "", System.currentTimeMillis(), note, lines)
        val arr = loadJsonArray("stock_takes")
        val linesArr = JSONArray()
        lines.forEach { l ->
            linesArr.put(JSONObject().apply {
                put("productId", l.productId); put("productName", l.productName)
                put("systemQty", l.systemQty); put("countedQty", l.countedQty); put("unit", l.unit)
            })
        }
        arr.put(JSONObject().apply {
            put("id", st.id); put("warehouseId", st.warehouseId)
            put("warehouseName", st.warehouseName); put("timestamp", st.timestamp)
            put("note", st.note); put("lines", linesArr)
        })
        saveJsonArray("stock_takes", arr)
        if (applyAdjust) {
            lines.filter { kotlin.math.abs(it.diff) > 0.001 }.forEach { l ->
                if (l.diff > 0) entry(l.productId, warehouseId, l.diff, null, "شمارش انبار", 0L, "تعدیل مثبت")
                else exit(l.productId, warehouseId, -l.diff, null, "", "تعدیل منفی شمارش")
            }
        }
        logActivity("STOCK_TAKE", st.warehouseName)
        return st
    }

    // ----- چک -----
    fun getChecks(): List<BankCheck> {
        val arr = loadJsonArray("bank_checks")
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            BankCheck(
                id = o.getLong("id"), type = o.getString("type"),
                personName = o.getString("personName"), amount = o.getDouble("amount"),
                dueDate = o.getLong("dueDate"), bankName = o.optString("bankName", ""),
                checkNumber = o.optString("checkNumber", ""), status = o.optString("status", "PENDING"),
                note = o.optString("note", ""), createdAt = o.optLong("createdAt", 0L)
            )
        }.sortedBy { it.dueDate }
    }

    fun addCheck(c: BankCheck): BankCheck {
        val withId = c.copy(id = next())
        val arr = loadJsonArray("bank_checks")
        arr.put(JSONObject().apply {
            put("id", withId.id); put("type", withId.type); put("personName", withId.personName)
            put("amount", withId.amount); put("dueDate", withId.dueDate)
            put("bankName", withId.bankName); put("checkNumber", withId.checkNumber)
            put("status", withId.status); put("note", withId.note); put("createdAt", withId.createdAt)
        })
        saveJsonArray("bank_checks", arr)
        logActivity("CHECK", withId.personName)
        return withId
    }

    fun updateCheckStatus(id: Long, status: String) {
        val list = getChecks().map { if (it.id == id) it.copy(status = status) else it }
        val arr = JSONArray()
        list.forEach { c ->
            arr.put(JSONObject().apply {
                put("id", c.id); put("type", c.type); put("personName", c.personName)
                put("amount", c.amount); put("dueDate", c.dueDate)
                put("bankName", c.bankName); put("checkNumber", c.checkNumber)
                put("status", c.status); put("note", c.note); put("createdAt", c.createdAt)
            })
        }
        saveJsonArray("bank_checks", arr)
    }

    fun getDueChecks(withinDays: Int = 7): List<BankCheck> {
        val now = System.currentTimeMillis()
        val until = now + withinDays * 24L * 3600 * 1000
        return getChecks().filter { it.status == "PENDING" && it.dueDate in now..until }
    }

    // ----- هزینه‌ها -----
    fun getExpenses(): List<Expense> {
        val arr = loadJsonArray("expenses")
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Expense(
                id = o.getLong("id"), title = o.getString("title"),
                category = o.getString("category"), amount = o.getDouble("amount"),
                timestamp = o.getLong("timestamp"), note = o.optString("note", "")
            )
        }.sortedByDescending { it.timestamp }
    }

    fun addExpense(e: Expense): Expense {
        val withId = e.copy(id = next())
        val arr = loadJsonArray("expenses")
        arr.put(JSONObject().apply {
            put("id", withId.id); put("title", withId.title); put("category", withId.category)
            put("amount", withId.amount); put("timestamp", withId.timestamp); put("note", withId.note)
        })
        saveJsonArray("expenses", arr)
        logActivity("EXPENSE", withId.title)
        return withId
    }

    fun expensesTotal(days: Int = 30): Double {
        val since = System.currentTimeMillis() - days * 24L * 3600 * 1000
        return getExpenses().filter { it.timestamp >= since }.sumOf { it.amount }
    }

    // ----- پیشنهاد خرید هوشمند -----
    fun getPurchaseSuggestions(daysLookback: Int = 30, thresholdDays: Int = 14): List<PurchaseSuggestion> {
        return getStockForecast(daysLookback)
            .filter { it.daysUntilEmpty in 0..thresholdDays }
            .map {
                val suggested = (it.avgDailySales * 30).coerceAtLeast(1.0)
                PurchaseSuggestion(
                    it.productId, it.productName, it.currentStock,
                    it.avgDailySales, it.daysUntilEmpty, suggested, it.unit
                )
            }
    }

    // ----- پیش‌فاکتور -----
    fun getProformas(): List<Proforma> {
        val arr = loadJsonArray("proformas")
        return (0 until arr.length()).map { parseProforma(arr.getJSONObject(it)) }
            .sortedByDescending { it.timestamp }
    }

    private fun parseProforma(o: JSONObject): Proforma {
        val itemsArr = o.getJSONArray("items")
        val items = (0 until itemsArr.length()).map { i ->
            val it = itemsArr.getJSONObject(i)
            SaleItem(
                productId = it.getLong("productId"),
                variantId = if (it.isNull("variantId")) null else it.optLong("variantId"),
                productName = it.getString("productName"),
                variantName = it.optString("variantName", ""),
                unit = it.optString("unit", "عدد"),
                quantity = it.getDouble("quantity"),
                unitPrice = it.getDouble("unitPrice"),
                lineTotal = it.optDouble("lineTotal", it.getDouble("quantity") * it.getDouble("unitPrice"))
            )
        }
        return Proforma(
            id = o.getLong("id"), number = o.getString("number"),
            customerId = if (o.isNull("customerId")) null else o.optLong("customerId"),
            customerName = o.getString("customerName"), items = items,
            subtotal = o.getDouble("subtotal"), total = o.getDouble("total"),
            expiresAt = o.getLong("expiresAt"), status = o.optString("status", "OPEN"),
            timestamp = o.getLong("timestamp"), note = o.optString("note", "")
        )
    }

    fun saveProforma(customerId: Long?, customerName: String, items: List<SaleItem>, hoursValid: Int = 24, note: String = ""): Proforma {
        val sub = items.sumOf { it.lineTotal }
        val seq = prefs.getInt("proforma_seq", 200) + 1
        prefs.edit().putInt("proforma_seq", seq).apply()
        val p = Proforma(
            id = next(), number = "PF-$seq", customerId = customerId,
            customerName = customerName.ifBlank { "عمومی" }, items = items,
            subtotal = sub, total = sub,
            expiresAt = System.currentTimeMillis() + hoursValid * 3600L * 1000,
            timestamp = System.currentTimeMillis(), note = note
        )
        val arr = loadJsonArray("proformas")
        val itemsArr = JSONArray()
        items.forEach { it ->
            itemsArr.put(JSONObject().apply {
                put("productId", it.productId)
                if (it.variantId != null) put("variantId", it.variantId) else put("variantId", JSONObject.NULL)
                put("productName", it.productName); put("variantName", it.variantName)
                put("quantity", it.quantity); put("unitPrice", it.unitPrice); put("unit", it.unit)
            })
        }
        arr.put(JSONObject().apply {
            put("id", p.id); put("number", p.number)
            if (p.customerId != null) put("customerId", p.customerId) else put("customerId", JSONObject.NULL)
            put("customerName", p.customerName); put("items", itemsArr)
            put("subtotal", p.subtotal); put("total", p.total)
            put("expiresAt", p.expiresAt); put("status", p.status)
            put("timestamp", p.timestamp); put("note", p.note)
        })
        saveJsonArray("proformas", arr)
        logActivity("PROFORMA", p.number)
        return p
    }

    // ----- سفارش ویزیتور -----
    fun getVisitOrders(): List<VisitOrder> {
        val arr = loadJsonArray("visit_orders")
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            val itemsArr = o.getJSONArray("items")
            val items = (0 until itemsArr.length()).map { i ->
                val it = itemsArr.getJSONObject(i)
                SaleItem(
                    productId = it.getLong("productId"),
                    productName = it.getString("productName"),
                    unit = it.optString("unit", "عدد"),
                    quantity = it.getDouble("quantity"),
                    unitPrice = it.getDouble("unitPrice"),
                    lineTotal = it.optDouble("lineTotal", it.getDouble("quantity") * it.getDouble("unitPrice"))
                )
            }
            VisitOrder(
                id = o.getLong("id"), number = o.getString("number"),
                customerName = o.getString("customerName"),
                customerPhone = o.optString("customerPhone", ""),
                items = items, total = o.getDouble("total"),
                status = o.optString("status", "PENDING"),
                timestamp = o.getLong("timestamp"),
                note = o.optString("note", ""), location = o.optString("location", "")
            )
        }.sortedByDescending { it.timestamp }
    }

    fun addVisitOrder(customerName: String, phone: String, items: List<SaleItem>, note: String, location: String): VisitOrder {
        val seq = prefs.getInt("visit_seq", 300) + 1
        prefs.edit().putInt("visit_seq", seq).apply()
        val total = items.sumOf { it.lineTotal }
        val v = VisitOrder(
            id = next(), number = "VO-$seq", customerName = customerName,
            customerPhone = phone, items = items, total = total,
            timestamp = System.currentTimeMillis(), note = note, location = location
        )
        val arr = loadJsonArray("visit_orders")
        val itemsArr = JSONArray()
        items.forEach { it ->
            itemsArr.put(JSONObject().apply {
                put("productId", it.productId); put("productName", it.productName)
                put("quantity", it.quantity); put("unitPrice", it.unitPrice); put("unit", it.unit)
            })
        }
        arr.put(JSONObject().apply {
            put("id", v.id); put("number", v.number); put("customerName", v.customerName)
            put("customerPhone", v.customerPhone); put("items", itemsArr); put("total", v.total)
            put("status", v.status); put("timestamp", v.timestamp)
            put("note", v.note); put("location", v.location)
        })
        saveJsonArray("visit_orders", arr)
        logActivity("VISIT_ORDER", v.number)
        return v
    }

    // ----- باندل -----
    fun getBundles(): List<SaleBundle> {
        val arr = loadJsonArray("bundles")
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            val ids = o.getJSONArray("productIds")
            SaleBundle(
                id = o.getLong("id"), name = o.getString("name"),
                productIds = (0 until ids.length()).map { i -> ids.getLong(i) },
                discountPercent = o.getDouble("discountPercent")
            )
        }
    }

    fun addBundle(name: String, productIds: List<Long>, discountPercent: Double): SaleBundle {
        val b = SaleBundle(next(), name, productIds, discountPercent)
        val arr = loadJsonArray("bundles")
        val ids = JSONArray(); productIds.forEach { ids.put(it) }
        arr.put(JSONObject().apply {
            put("id", b.id); put("name", b.name); put("productIds", ids); put("discountPercent", b.discountPercent)
        })
        saveJsonArray("bundles", arr)
        return b
    }

    /** سود خالص با کسر هزینه‌ها */
    fun getNetProfitWithExpenses(days: Int = 30): Double {
        val pl = getPLReport(days)
        return pl.netProfit - expensesTotal(days)
    }

    /** خلاصه یک‌صفحه‌ای روز برای گزارش شبانه */
    fun buildNightlySummary(): String {
        val kpi = getDailyKpi()
        val sales = getSales().filter {
            it.status == "COMPLETED" && it.timestamp >= startOfToday()
        }
        val soldOut = getInventoryRows().filter { it.quantity <= 0.001 }
        val low = getInventoryRows().filter { it.quantity > 0 && it.quantity <= 5 }
        val exp = getExpiringItems(7)
        return buildString {
            append("فروش امروز: ${FormatUtils.money(kpi.todaySalesTotal)} (${kpi.todaySalesCount} فاکتور)\n")
            append("سود تقریبی: ${FormatUtils.money(kpi.todayProfit)}\n")
            if (soldOut.isNotEmpty()) {
                append("تمام‌شده: ${soldOut.take(5).joinToString { it.productName }}\n")
            } else append("تمام‌شده: ندارد\n")
            if (low.isNotEmpty()) append("کمبود: ${low.size} قلم\n")
            if (exp.isNotEmpty()) append("نزدیک انقضا (۷روز): ${exp.size} قلم\n")
            if (sales.isEmpty()) append("امروز فروشی ثبت نشده.")
        }
    }

    // ----- تحویل پیک -----
    fun getDeliveries(): List<DeliveryOrder> {
        val arr = loadJsonArray("deliveries")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            DeliveryOrder(
                id = o.getLong("id"),
                saleId = o.optLong("saleId", 0L),
                customerName = o.optString("customerName", ""),
                customerPhone = o.optString("customerPhone", ""),
                address = o.optString("address", ""),
                courierName = o.optString("courierName", ""),
                status = o.optString("status", "READY"),
                note = o.optString("note", ""),
                total = o.optDouble("total", 0.0),
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
            )
        }.sortedByDescending { it.updatedAt }
    }

    fun saveDeliveries(list: List<DeliveryOrder>) {
        val arr = org.json.JSONArray()
        list.forEach {
            arr.put(org.json.JSONObject().apply {
                put("id", it.id); put("saleId", it.saleId)
                put("customerName", it.customerName); put("customerPhone", it.customerPhone)
                put("address", it.address); put("courierName", it.courierName)
                put("status", it.status); put("note", it.note)
                put("total", it.total); put("createdAt", it.createdAt); put("updatedAt", it.updatedAt)
            })
        }
        saveJsonArray("deliveries", arr)
    }

    fun addDelivery(
        customerName: String,
        phone: String,
        address: String,
        courier: String,
        total: Double,
        note: String = "",
        saleId: Long = 0L
    ): DeliveryOrder {
        val list = getDeliveries().toMutableList()
        val d = DeliveryOrder(
            id = next(), saleId = saleId, customerName = customerName.trim(),
            customerPhone = phone.trim(), address = address.trim(),
            courierName = courier.trim(), status = "READY", note = note.trim(),
            total = total, createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        list.add(0, d)
        saveDeliveries(list)
        logActivity("DELIVERY_ADD", d.customerName)
        return d
    }

    fun updateDeliveryStatus(id: Long, status: String) {
        val list = getDeliveries().toMutableList()
        val idx = list.indexOfFirst { it.id == id }
        if (idx < 0) return
        list[idx] = list[idx].copy(status = status, updatedAt = System.currentTimeMillis())
        saveDeliveries(list)
        logActivity("DELIVERY_STATUS", "$id -> $status")
    }



    // ----- سفارش ویزیتور -----
    fun getVisitorOrders(): List<VisitorOrder> {
        val arr = loadJsonArray("visitor_orders")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val itemsArr = o.optJSONArray("items") ?: org.json.JSONArray()
            val items = (0 until itemsArr.length()).map { j ->
                val it = itemsArr.getJSONObject(j)
                VisitorLine(
                    productName = it.optString("productName", ""),
                    quantity = it.optDouble("quantity", 0.0),
                    unitPrice = it.optDouble("unitPrice", 0.0)
                )
            }
            VisitorOrder(
                id = o.getLong("id"),
                visitorName = o.optString("visitorName", ""),
                customerName = o.optString("customerName", ""),
                customerPhone = o.optString("customerPhone", ""),
                address = o.optString("address", ""),
                discountPercent = o.optDouble("discountPercent", 0.0),
                items = items,
                note = o.optString("note", ""),
                status = o.optString("status", "OPEN"),
                total = o.optDouble("total", 0.0),
                createdAt = o.optLong("createdAt", System.currentTimeMillis())
            )
        }.sortedByDescending { it.createdAt }
    }

    fun saveVisitorOrders(list: List<VisitorOrder>) {
        val arr = org.json.JSONArray()
        list.forEach { v ->
            val itemsArr = org.json.JSONArray()
            v.items.forEach { it ->
                itemsArr.put(org.json.JSONObject().apply {
                    put("productName", it.productName)
                    put("quantity", it.quantity)
                    put("unitPrice", it.unitPrice)
                })
            }
            arr.put(org.json.JSONObject().apply {
                put("id", v.id)
                put("visitorName", v.visitorName)
                put("customerName", v.customerName)
                put("customerPhone", v.customerPhone)
                put("address", v.address)
                put("discountPercent", v.discountPercent)
                put("items", itemsArr)
                put("note", v.note)
                put("status", v.status)
                put("total", v.total)
                put("createdAt", v.createdAt)
            })
        }
        saveJsonArray("visitor_orders", arr)
    }

    fun addVisitorOrder(
        visitorName: String,
        customerName: String,
        customerPhone: String,
        address: String,
        discountPercent: Double,
        items: List<VisitorLine>,
        note: String = ""
    ): VisitorOrder {
        val sub = items.sumOf { it.lineTotal }
        val total = sub * (1 - discountPercent / 100.0)
        val order = VisitorOrder(
            id = System.currentTimeMillis(),
            visitorName = visitorName,
            customerName = customerName,
            customerPhone = customerPhone,
            address = address,
            discountPercent = discountPercent,
            items = items,
            note = note,
            status = "OPEN",
            total = total
        )
        val list = getVisitorOrders().toMutableList()
        list.add(0, order)
        saveVisitorOrders(list)
        touchAppClock()
        return order
    }

    fun updateVisitorStatus(id: Long, status: String) {
        val list = getVisitorOrders().map {
            if (it.id == id) it.copy(status = status) else it
        }
        saveVisitorOrders(list)
        touchAppClock()
    }


    // ========== ارتقای تجاری ۵.۰ ==========

    /** برگشت از فروش: افزایش موجودی + کاهش بدهی + فاکتور منفی */
    fun returnSale(originalId: Long, note: String = ""): Sale? {
        val original = getSale(originalId) ?: run {
            lastError = "فاکتور پیدا نشد"
            return null
        }
        if (original.status == "RETURNED" || original.status == "CANCELLED") {
            lastError = "این فاکتور قبلاً برگشت/لغو شده"
            return null
        }
        if (original.originalSaleId > 0L) {
            lastError = "نمی‌توان فاکتور برگشت را دوباره برگشت زد"
            return null
        }
        // افزایش موجودی
        for (item in original.items) {
            if (item.productId > 0L) {
                entry(item.productId, original.warehouseId, item.quantity, item.variantId, "", 0L, "برگشت ${original.invoiceNumber}")
            }
        }
        // کاهش بدهی نسیه
        if (original.isCredit && original.customerId != null) {
            val debt = (original.total - original.paidAmount).coerceAtLeast(0.0)
            getCustomer(original.customerId)?.let { c ->
                updateCustomer(c.copy(creditBalance = (c.creditBalance - debt).coerceAtLeast(0.0)))
            }
        }
        // علامت‌گذاری اصل
        val sales = getSales().toMutableList()
        val idx = sales.indexOfFirst { it.id == originalId }
        if (idx >= 0) sales[idx] = sales[idx].copy(status = "RETURNED")

        val retItems = original.items.map { it.copy() }
        val ret = Sale(
            id = next(),
            invoiceNumber = "RET-${original.invoiceNumber}",
            customerId = original.customerId,
            customerName = original.customerName,
            items = retItems,
            subtotal = -original.subtotal,
            discountAmount = -original.discountAmount,
            discountPercent = original.discountPercent,
            vatPercent = original.vatPercent,
            vatAmount = -original.vatAmount,
            total = -original.total,
            paidAmount = -original.paidAmount,
            isCredit = original.isCredit,
            paymentMethod = original.paymentMethod,
            note = "برگشت از ${original.invoiceNumber}. $note",
            warehouseId = original.warehouseId,
            timestamp = TimeHelper.stamp(),
            status = "COMPLETED",
            originalSaleId = original.id,
            cashPaid = -original.cashPaid,
            cardPaid = -original.cardPaid,
            creditPaid = -original.creditPaid
        )
        sales.add(0, ret)
        saveSales(sales)
        logActivity("SALE_RETURN", ret.invoiceNumber)
        lastError = ""
        return ret
    }

    /** جستجوی سراسری */
    fun globalSearch(query: String): List<SearchHit> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val hits = mutableListOf<SearchHit>()
        getProducts().filter {
            it.name.contains(q, true) || it.code.contains(q, true) || it.barcode.contains(q, true)
        }.take(15).forEach {
            hits.add(SearchHit("PRODUCT", it.id, it.name, "کالا · ${it.code} · ${FormatUtils.money(it.salePrice)}"))
        }
        getCustomers().filter {
            it.name.contains(q, true) || it.phone.contains(q) || it.address.contains(q, true)
        }.take(10).forEach {
            hits.add(SearchHit("CUSTOMER", it.id, it.name, "مشتری · ${it.phone} · بدهی ${FormatUtils.money(it.creditBalance)}"))
        }
        getSales().filter {
            it.invoiceNumber.contains(q, true) || it.customerName.contains(q, true)
        }.take(10).forEach {
            hits.add(SearchHit("SALE", it.id, it.invoiceNumber, "${it.customerName} · ${FormatUtils.money(it.total)} · ${it.status}"))
        }
        getDeliveries().filter {
            it.customerName.contains(q, true) || it.customerPhone.contains(q) || it.address.contains(q, true)
        }.take(8).forEach {
            hits.add(SearchHit("DELIVERY", it.id, it.customerName, "پیک · ${it.statusLabel()} · ${FormatUtils.money(it.total)}"))
        }
        try {
            getVisitorOrders().filter {
                it.customerName.contains(q, true) || it.visitorName.contains(q, true)
            }.take(8).forEach {
                hits.add(SearchHit("VISITOR", it.id, it.customerName, "ویزیتور · ${it.statusLabel()} · ${FormatUtils.money(it.total)}"))
            }
        } catch (_: Exception) {}
        return hits
    }

    /** سود کالایی در بازه روز */
    fun getProductProfits(days: Int = 30): List<ProductProfit> {
        val since = System.currentTimeMillis() - days * 24L * 60 * 60 * 1000
        val map = mutableMapOf<String, Triple<Double, Double, Double>>() // qty, rev, cogs
        getSales().filter { it.timestamp >= since && it.status == "COMPLETED" && it.originalSaleId == 0L }.forEach { sale ->
            sale.items.forEach { item ->
                val p = getProduct(item.productId)
                val cost = p?.purchasePrice ?: 0.0
                val prev = map[item.productName] ?: Triple(0.0, 0.0, 0.0)
                map[item.productName] = Triple(
                    prev.first + item.quantity,
                    prev.second + item.amount(),
                    prev.third + cost * item.quantity
                )
            }
        }
        return map.map { (name, v) ->
            ProductProfit(name, v.first, v.second, v.third, v.second - v.third)
        }.sortedByDescending { it.profit }
    }

    /** مقایسه این ماه با ماه قبل */
    fun getMonthComparison(): Triple<Double, Double, Double> {
        val cal = java.util.Calendar.getInstance()
        val now = System.currentTimeMillis()
        cal.timeInMillis = now
        cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        val monthStart = cal.timeInMillis
        cal.add(java.util.Calendar.MONTH, -1)
        val prevStart = cal.timeInMillis
        val thisMonth = getSales().filter { it.timestamp >= monthStart && it.status == "COMPLETED" && it.total > 0 }.sumOf { it.total }
        val prevMonth = getSales().filter { it.timestamp >= prevStart && it.timestamp < monthStart && it.status == "COMPLETED" && it.total > 0 }.sumOf { it.total }
        val growth = if (prevMonth > 0) ((thisMonth - prevMonth) / prevMonth) * 100.0 else 0.0
        return Triple(thisMonth, prevMonth, growth)
    }

    /** پیشنهاد خرید بر اساس حداقل موجودی */
    fun getReorderSuggestions(): List<PurchaseSuggestion> {
        val stockMap = getAllStock().groupBy { it.productId }.mapValues { it.value.sumOf { s -> s.quantity } }
        val out = mutableListOf<PurchaseSuggestion>()
        for (p in getProducts()) {
            val stock = stockMap[p.id] ?: 0.0
            val min = if (p.minStock > 0) p.minStock else 5.0
            if (stock <= min) {
                val need = if (p.maxStock > min) p.maxStock - stock else (min * 2 - stock).coerceAtLeast(min)
                out.add(
                    PurchaseSuggestion(
                        productId = p.id,
                        productName = p.name,
                        currentStock = stock,
                        avgDailySales = 0.0,
                        daysLeft = 0,
                        suggestedQty = need,
                        unit = p.unit
                    )
                )
            }
        }
        return out.sortedBy { it.currentStock }
    }

    /** تولید بارکد داخلی */
    fun generateInternalBarcode(): String {
        val n = prefs.getInt("barcode_seq", 100000) + 1
        prefs.edit().putInt("barcode_seq", n).apply()
        return "KLN$n"
    }

    /** ورود کالا از CSV: name,code,barcode,unit,purchase,sale,category,min,max */
    fun importProductsCsv(lines: List<String>): Int {
        var count = 0
        for (line in lines.drop(1)) { // skip header if any
            val cols = line.split(',').map { it.trim().removeSurrounding("\"") }
            if (cols.isEmpty() || cols[0].isBlank()) continue
            val name = cols.getOrNull(0) ?: continue
            if (name.equals("name", true) || name == "نام") continue
            val code = cols.getOrNull(1)?.ifBlank { "C${System.currentTimeMillis()%100000}" } ?: "C${next()}"
            val barcode = cols.getOrNull(2)?.ifBlank { generateInternalBarcode() } ?: generateInternalBarcode()
            val unit = cols.getOrNull(3)?.ifBlank { "عدد" } ?: "عدد"
            val purchase = cols.getOrNull(4)?.toDoubleOrNull() ?: 0.0
            val sale = cols.getOrNull(5)?.toDoubleOrNull() ?: 0.0
            val cat = cols.getOrNull(6)?.ifBlank { "عمومی" } ?: "عمومی"
            val minS = cols.getOrNull(7)?.toDoubleOrNull() ?: 0.0
            val maxS = cols.getOrNull(8)?.toDoubleOrNull() ?: 0.0
            val p = addProduct(name, code, barcode, unit, false, false, purchase, sale, cat, "")
            if (minS > 0 || maxS > 0) updateProduct(p.copy(minStock = minS, maxStock = maxS))
            count++
        }
        logActivity("IMPORT_PRODUCTS", "$count")
        return count
    }

    fun importCustomersCsv(lines: List<String>): Int {
        var count = 0
        for (line in lines) {
            val cols = line.split(',').map { it.trim().removeSurrounding("\"") }
            if (cols.isEmpty() || cols[0].isBlank()) continue
            val name = cols[0]
            if (name.equals("name", true) || name == "نام") continue
            val phone = cols.getOrNull(1).orEmpty()
            val address = cols.getOrNull(2).orEmpty()
            val limit = cols.getOrNull(3)?.toDoubleOrNull() ?: 0.0
            val c = addCustomer(name, phone, address)
            if (limit > 0) updateCustomer(c.copy(creditLimit = limit))
            count++
        }
        logActivity("IMPORT_CUSTOMERS", "$count")
        return count
    }

    fun customersOverCredit(): List<Customer> =
        getCustomers().filter { it.creditLimit > 0 && it.creditBalance >= it.creditLimit * 0.9 }

    fun loyaltyTierOf(points: Int): String = when {
        points >= 5000 -> "GOLD"
        points >= 1500 -> "SILVER"
        else -> "NORMAL"
    }

    fun refreshLoyaltyTier(customerId: Long) {
        getCustomer(customerId)?.let { c ->
            updateCustomer(c.copy(loyaltyTier = loyaltyTierOf(c.loyaltyPoints)))
        }
    }

}
