package com.example.anbar

/**
 * بخش ۱ - مدل‌های مدیریت پیشرفته انبار
 */

data class Warehouse(
    val id: Long,
    val name: String,          // مغازه، انبار پشتی، ...
    val type: String = "MAIN", // MAIN, BACKUP, OTHER
    val isDefault: Boolean = false
)

data class Product(
    val id: Long,
    val name: String,
    val code: String,
    val barcode: String = "",
    val unit: String = "عدد",
    val hasVariants: Boolean = false,
    val trackBatch: Boolean = false,
    val description: String = "",
    val purchasePrice: Double = 0.0,
    val salePrice: Double = 0.0,
    val category: String = "عمومی",
    val imagePath: String = "",
    val minStock: Double = 0.0,
    val maxStock: Double = 0.0
)

data class ProductVariant(
    val id: Long,
    val productId: Long,
    val name: String,           // مثلاً "قرمز - L"
    val color: String = "",
    val size: String = "",
    val barcode: String = "",
    val sku: String = ""
)

data class StockItem(
    val id: Long,
    val productId: Long,
    val variantId: Long? = null, // null = بدون واریانت
    val warehouseId: Long,
    val quantity: Double,
    val batchNumber: String = "",
    val expiryDate: Long = 0L,   // timestamp, 0 = بدون انقضا
    val unitCost: Double = 0.0
)

data class StockTransfer(
    val id: Long,
    val fromWarehouseId: Long,
    val toWarehouseId: Long,
    val productId: Long,
    val variantId: Long? = null,
    val quantity: Double,
    val batchNumber: String = "",
    val note: String = "",
    val timestamp: Long
)

data class Transaction(
    val id: Long,
    val productId: Long,
    val variantId: Long? = null,
    val warehouseId: Long,
    val productName: String,
    val type: String, // ENTRY, EXIT, TRANSFER
    val amount: Double,
    val batchNumber: String = "",
    val note: String = "",
    val timestamp: Long
)

// برای نمایش ترکیبی در لیست
data class InventoryRow(
    val stockId: Long,
    val productId: Long,
    val variantId: Long?,
    val productName: String,
    val variantName: String,
    val warehouseName: String,
    val quantity: Double,
    val unit: String,
    val batchNumber: String,
    val expiryDate: Long,
    val barcode: String,
    val isExpiringSoon: Boolean = false,
    val isExpired: Boolean = false
)

/**
 * بخش ۲ - سیستم فروش حرفه‌ای (POS)
 */

data class Customer(
    val id: Long,
    val name: String,
    val phone: String = "",
    val address: String = "",
    var creditBalance: Double = 0.0, // بدهی فعلی
    var loyaltyPoints: Int = 0,      // امتیاز باشگاه مشتریان
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val creditLimit: Double = 0.0, // 0 = بدون سقف
    val loyaltyTier: String = "NORMAL" // NORMAL | SILVER | GOLD
)

data class SaleItem(
    val productId: Long,
    val variantId: Long? = null,
    val productName: String,
    val variantName: String = "",
    val unit: String = "عدد",
    val quantity: Double,
    val unitPrice: Double,
    val discountPercent: Double = 0.0,
    val lineTotal: Double = 0.0
) {
    /** اگر lineTotal صفر ذخیره شده، از quantity×price محاسبه می‌شود */
    fun amount(): Double =
        if (lineTotal > 0) lineTotal
        else quantity * unitPrice * (1.0 - discountPercent / 100.0)
}

data class Sale(
    val id: Long,
    val invoiceNumber: String,
    val customerId: Long? = null, // null = فروش نقدی بدون مشتری
    val customerName: String = "مهمان",
    val items: List<SaleItem>,
    val subtotal: Double,
    val discountAmount: Double,
    val discountPercent: Double,
    val vatPercent: Double,
    val vatAmount: Double,
    val total: Double,
    val paidAmount: Double,
    val isCredit: Boolean, // فروش نسیه
    val paymentMethod: String, // CASH, CARD, CREDIT, MIXED
    val note: String = "",
    val warehouseId: Long,
    val timestamp: Long,
    val status: String = "COMPLETED", // COMPLETED, CANCELLED, RETURNED
    val cashPaid: Double = 0.0,
    val cardPaid: Double = 0.0,
    val creditPaid: Double = 0.0,
    val originalSaleId: Long = 0L, // برای فاکتور برگشت
    val cashierName: String = ""
)

data class AppSettings(
    val shopName: String = "فروشگاه من",
    val vatPercent: Double = 9.0,
    val currency: String = "TOMAN",
    val defaultWarehouseId: Long = 0L,
    val primaryColor: String = "#1565C0",
    val logoPath: String = "",
    val invoiceFooter: String = "از خرید شما متشکریم",
    val shortcuts: String = "POS,CUSTOMERS,DASHBOARD",
    val rateUsdToToman: Double = 900_000.0,
    val rateUsdtToToman: Double = 900_000.0,
    val shopAddress: String = "",
    val shopPhone: String = "",
    /** SHAMSI یا GREGORIAN */
    val calendarType: String = "SHAMSI",
    val cashierOnlyMode: Boolean = false,
    val autoLogoutMinutes: Int = 0, // 0 = خاموش
    val invoiceShowPoetry: Boolean = true,
    val invoiceExtraNote: String = ""
)

/** آیتم سبد خرید موقت */
data class CartItem(
    val productId: Long,
    val variantId: Long? = null,
    val productName: String,
    val variantName: String = "",
    val unit: String,
    var quantity: Double,
    var unitPrice: Double,
    var discountPercent: Double = 0.0
) {
    val lineTotal: Double
        get() = quantity * unitPrice * (1 - discountPercent / 100.0)
}

/**
 * بخش ۴ - مدیریت تامین‌کنندگان
 */

data class Supplier(
    val id: Long,
    val name: String,
    val phone: String = "",
    val address: String = "",
    var payableBalance: Double = 0.0, // بدهی ما به تامین‌کننده
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

data class PurchaseItem(
    val productId: Long,
    val variantId: Long? = null,
    val productName: String,
    val quantity: Double,
    val unitCost: Double,
    val lineTotal: Double
)

data class Purchase(
    val id: Long,
    val invoiceNumber: String,
    val supplierId: Long,
    val supplierName: String,
    val items: List<PurchaseItem>,
    val subtotal: Double,
    val paidAmount: Double,
    val isCredit: Boolean, // خرید نسیه از تامین‌کننده
    val warehouseId: Long,
    val note: String = "",
    val timestamp: Long,
    val status: String = "COMPLETED"
)

/**
 * بخش ۶ - چندکاربره و بکاپ
 */

data class AppUser(
    val id: Long,
    val username: String,
    val displayName: String,
    val pinHash: String,          // PIN ساده هش‌شده
    val role: String,             // ADMIN, CASHIER, WAREHOUSE
    val branchId: Long? = null,   // شعبه مرتبط (null = همه)
    val isActive: Boolean = true
)

data class Branch(
    val id: Long,
    val name: String,
    val warehouseId: Long,        // انبار اصلی این شعبه
    val isActive: Boolean = true
)

/** سطح دسترسی بر اساس نقش */
object Permissions {
    fun canSell(role: String) = role in listOf("ADMIN", "CASHIER")
    fun canManageStock(role: String) = role in listOf("ADMIN", "WAREHOUSE")
    fun canManageUsers(role: String) = role == "ADMIN"
    fun canViewReports(role: String) = role in listOf("ADMIN", "CASHIER")
    fun canManageSuppliers(role: String) = role == "ADMIN"
    fun canManageSettings(role: String) = role == "ADMIN"
}

/**
 * بخش ۷ - امنیت
 */
data class ActivityLog(
    val id: Long,
    val userId: Long?,
    val username: String,
    val action: String,      // LOGIN, SALE, ENTRY, EXIT, PURCHASE, BACKUP, SETTINGS, ...
    val detail: String,
    val timestamp: Long
)

/**
 * بخش ۱۰ - یکپارچه‌سازی
 */
data class CurrencyRate(
    val code: String,      // IRR, USD, EUR, AED, TRY
    val name: String,
    val rateToBase: Double // نسبت به ارز پایه (تومان)
)

data class WebhookConfig(
    val enabled: Boolean = false,
    val url: String = "",
    val secret: String = "",
    val onSale: Boolean = true,
    val onPurchase: Boolean = true,
    val onStockChange: Boolean = false
)

/**
 * بخش ۱۱ - تحویل و بازتحویل همکار (امانی)
 */
data class Colleague(
    val id: Long,
    val name: String,
    val phone: String = "",
    val notes: String = "",
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

data class HandoverItem(
    val productId: Long,
    val variantId: Long? = null,
    val productName: String,
    val quantity: Double,
    val unit: String = "عدد"
)

data class Handover(
    val id: Long,
    val type: String, // HANDOVER (تحویل) یا RETURN (بازتحویل)
    val colleagueId: Long,
    val colleagueName: String,
    val warehouseId: Long,
    val items: List<HandoverItem>,
    val note: String = "",
    val timestamp: Long,
    val receiptNumber: String
)

/** مانده کالای نزد همکار */
data class ColleagueBalance(
    val productId: Long,
    val productName: String,
    val unit: String,
    val quantity: Double
)

// ==================== نسخه تجاری ۳.۰ ====================

/** شیفت صندوق */
data class CashShift(
    val id: Long,
    val openedAt: Long,
    val closedAt: Long = 0L,
    val openingCash: Double,
    val closingCash: Double = 0.0,
    val expectedCash: Double = 0.0, // محاسبه از فروش نقدی
    val variance: Double = 0.0,
    val openedBy: String = "",
    val note: String = "",
    val isOpen: Boolean = true
)

/** قیمت چندسطحی کالا */
data class ProductPrice(
    val productId: Long,
    val retail: Double = 0.0,      // خرده‌فروشی
    val wholesale: Double = 0.0,   // عمده
    val vip: Double = 0.0,         // VIP
    val colleague: Double = 0.0     // همکار
)

/** شمارش انبار */
data class StockTake(
    val id: Long,
    val warehouseId: Long,
    val warehouseName: String,
    val timestamp: Long,
    val note: String = "",
    val lines: List<StockTakeLine>
)

data class StockTakeLine(
    val productId: Long,
    val productName: String,
    val systemQty: Double,
    val countedQty: Double,
    val unit: String = "عدد"
) {
    val diff: Double get() = countedQty - systemQty
}

/** چک */
data class BankCheck(
    val id: Long,
    val type: String, // RECEIVABLE | PAYABLE
    val personName: String,
    val amount: Double,
    val dueDate: Long,
    val bankName: String = "",
    val checkNumber: String = "",
    val status: String = "PENDING", // PENDING, CLEARED, BOUNCED
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/** هزینه جاری */
data class Expense(
    val id: Long,
    val title: String,
    val category: String, // اجاره، حمل، بسته‌بندی، حقوق، سایر
    val amount: Double,
    val timestamp: Long,
    val note: String = ""
)

/** پیش‌فاکتور / رزرو */
data class Proforma(
    val id: Long,
    val number: String,
    val customerId: Long?,
    val customerName: String,
    val items: List<SaleItem>,
    val subtotal: Double,
    val total: Double,
    val expiresAt: Long, // رزرو تا این زمان
    val status: String = "OPEN", // OPEN, CONVERTED, EXPIRED, CANCELLED
    val timestamp: Long,
    val note: String = ""
)

/** سفارش ویزیتور */
data class VisitOrder(
    val id: Long,
    val number: String,
    val customerName: String,
    val customerPhone: String = "",
    val items: List<SaleItem>,
    val total: Double,
    val status: String = "PENDING", // PENDING, DELIVERED, CANCELLED
    val timestamp: Long,
    val note: String = "",
    val location: String = ""
)

/** باندل فروش */
data class SaleBundle(
    val id: Long,
    val name: String,
    val productIds: List<Long>,
    val discountPercent: Double
)

/** پیشنهاد خرید هوشمند */
data class PurchaseSuggestion(
    val productId: Long,
    val productName: String,
    val currentStock: Double,
    val avgDailySales: Double,
    val daysLeft: Int,
    val suggestedQty: Double,
    val unit: String
)


/** سفارش تحویل پیک */
data class DeliveryOrder(
    val id: Long,
    val saleId: Long = 0L,
    val customerName: String,
    val customerPhone: String = "",
    val address: String = "",
    val courierName: String = "",
    val status: String = "READY", // READY | IN_TRANSIT | DELIVERED | CANCELLED
    val note: String = "",
    val total: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun statusLabel(): String = when (status) {
        "READY" -> "آماده"
        "IN_TRANSIT" -> "در مسیر"
        "DELIVERED" -> "تحویل‌شده"
        "CANCELLED" -> "لغو"
        else -> status
    }
}

/** خط سفارش ویزیتور */
data class VisitorLine(
    val productName: String,
    val quantity: Double,
    val unitPrice: Double
) {
    val lineTotal: Double get() = quantity * unitPrice
}

/** سفارش ویزیتور ذخیره‌شده */
data class VisitorOrder(
    val id: Long,
    val visitorName: String = "",
    val customerName: String,
    val customerPhone: String = "",
    val address: String = "",
    val discountPercent: Double = 0.0,
    val items: List<VisitorLine> = emptyList(),
    val note: String = "",
    val status: String = "OPEN", // OPEN | CONFIRMED | DELIVERED | CANCELLED
    val total: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun statusLabel(): String = when (status) {
        "OPEN" -> "باز"
        "CONFIRMED" -> "تأیید شده"
        "DELIVERED" -> "تحویل"
        "CANCELLED" -> "لغو"
        else -> status
    }
}

/** سود هر کالا */
data class ProductProfit(
    val productName: String,
    val qtySold: Double,
    val revenue: Double,
    val cogs: Double,
    val profit: Double
)

/** نتیجه جستجوی سراسری */
data class SearchHit(
    val type: String, // PRODUCT | CUSTOMER | SALE | DELIVERY | VISITOR
    val id: Long,
    val title: String,
    val subtitle: String
)
