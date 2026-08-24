<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  cancelOrder, claimCoupon, completeOrder, createCommerceOrder, createCouponTemplate, createFlashSale,
  createVideoProduct, getClaimableCoupons, getCreatorOrders, getCreatorProducts, getCreatorRefunds,
  getMyCoupons, getMyOrders, getMyPublished, getMyRefunds, payOrder, requestRefund, reviewRefund, shipOrder
} from '../api'

const props = defineProps({ selectedProduct: { type: Object, default: null } })
const emit = defineEmits(['clear-product'])
const notice = ref('')
const products = ref([]), published = ref([]), orders = ref([]), creatorOrders = ref([])
const coupons = ref([]), claimable = ref([]), refunds = ref([]), creatorRefunds = ref([])
const productForm = reactive({ videoId: '', name: '', description: '', imageUrl: '', priceCent: 9900, stock: 100 })
const saleForm = reactive({ productId: '', salePriceCent: 4900, totalStock: 50, perUserLimit: 1, startsAt: '', endsAt: '' })
const couponForm = reactive({ name: '创作者限时券', thresholdCent: 5000, discountCent: 1000, totalStock: 100, startsAt: '', endsAt: '' })
const checkout = reactive({ userCouponId: '', receiverName: '', receiverPhone: '', receiverAddress: '' })
const activeProduct = computed(() => props.selectedProduct)

async function load() {
  try {
    const result = await Promise.all([getCreatorProducts(), getMyPublished(), getMyOrders(), getCreatorOrders(), getMyCoupons(), getMyRefunds(), getCreatorRefunds()])
    ;[products.value, published.value, orders.value, creatorOrders.value, coupons.value, refunds.value, creatorRefunds.value] = result.map(value => value || [])
    if (activeProduct.value?.productId) claimable.value = await getClaimableCoupons(activeProduct.value.creatorId || '') || []
  } catch (error) { notice.value = error.message }
}
async function act(task, message) { try { await task(); notice.value = message; await load() } catch (error) { notice.value = error.message } }
async function addProduct() { await act(() => createVideoProduct({ ...productForm, videoId: Number(productForm.videoId), priceCent: Number(productForm.priceCent), stock: Number(productForm.stock) }), '商品已挂载到视频') }
async function addSale() { await act(() => createFlashSale({ ...saleForm, productId: Number(saleForm.productId), salePriceCent: Number(saleForm.salePriceCent), totalStock: Number(saleForm.totalStock), perUserLimit: 1 }), '秒杀活动已创建') }
async function addCoupon() { await act(() => createCouponTemplate({ ...couponForm, thresholdCent: Number(couponForm.thresholdCent), discountCent: Number(couponForm.discountCent), totalStock: Number(couponForm.totalStock) }), '优惠券已创建') }
async function buy() {
  if (!activeProduct.value?.flashSaleId) return
  await act(() => createCommerceOrder({ flashSaleId: activeProduct.value.flashSaleId, userCouponId: checkout.userCouponId ? Number(checkout.userCouponId) : null, receiverName: checkout.receiverName, receiverPhone: checkout.receiverPhone, receiverAddress: checkout.receiverAddress }), '抢购资格已锁定，订单等待模拟支付')
  emit('clear-product')
}
async function refund(order) { const reason = window.prompt('请输入退款原因'); if (reason) await act(() => requestRefund(order.id, reason), '售后申请已提交') }
function money(cent) { return `¥${(Number(cent || 0) / 100).toFixed(2)}` }
watch(() => props.selectedProduct, async product => { if (product?.creatorId) claimable.value = await getClaimableCoupons(product.creatorId) || [] }, { immediate: true })
onMounted(load)
</script>

<template>
  <section class="commerce-shell">
    <div class="commerce-head"><div><div class="eyebrow">VIDEO COMMERCE // LIMITED DROP</div><h2>MARKET <em>77</em></h2><p>视频挂载商品、Redis Lua 秒杀、优惠券、订单与售后闭环。</p></div><div v-if="notice" class="status-strip ok">{{ notice }}</div></div>
    <div v-if="activeProduct" class="checkout-card"><div><small>SELECTED DROP</small><h3>{{ activeProduct.name }}</h3><p><del>{{ money(activeProduct.originalPriceCent) }}</del> <b>{{ money(activeProduct.salePriceCent) }}</b> · 剩余 {{ activeProduct.remainingStock }}</p></div><div class="checkout-fields"><select v-model="checkout.userCouponId" class="field"><option value="">不使用优惠券</option><option v-for="c in coupons.filter(x => x.status === 'AVAILABLE')" :key="c.id" :value="c.id">{{ c.name }} - {{ money(c.discountCent) }}</option></select><input v-model="checkout.receiverName" class="field" placeholder="收货人" /><input v-model="checkout.receiverPhone" class="field" placeholder="联系电话" /><input v-model="checkout.receiverAddress" class="field" placeholder="收货地址" /><button class="btn yellow" @click="buy">RESERVE NOW</button><button class="btn ghost" @click="$emit('clear-product')">CANCEL</button></div><div class="coupon-drop"><button v-for="c in claimable" :key="c.templateId" class="coupon-chip" @click="act(() => claimCoupon(c.templateId), '优惠券已领取')">领 {{ money(c.discountCent) }} 券</button></div></div>
    <div class="commerce-grid">
      <section class="panel"><h3>CREATOR // PRODUCT</h3><div class="form-row"><label>PUBLISHED VIDEO</label><select v-model="productForm.videoId" class="field"><option value="">选择已发布视频</option><option v-for="v in published" :key="v.id" :value="v.id">{{ v.description || v.id }}</option></select></div><input v-model="productForm.name" class="field" placeholder="商品名称" /><textarea v-model="productForm.description" class="textarea compact" placeholder="商品介绍"></textarea><div class="inline-fields"><input v-model.number="productForm.priceCent" class="field" type="number" placeholder="原价/分" /><input v-model.number="productForm.stock" class="field" type="number" placeholder="库存" /></div><button class="btn pink" @click="addProduct">ATTACH PRODUCT</button><div class="job-list"><div v-for="p in products" :key="p.productId" class="job"><div><b>{{ p.name }}</b><small>PRODUCT {{ p.productId }} · VIDEO {{ p.videoId }}</small></div><span class="badge">{{ p.activityStatus }}</span></div></div></section>
      <section class="panel"><h3>CREATOR // FLASH SALE</h3><select v-model="saleForm.productId" class="field"><option value="">选择商品</option><option v-for="p in products" :key="p.productId" :value="p.productId">{{ p.name }}</option></select><div class="inline-fields"><input v-model.number="saleForm.salePriceCent" class="field" type="number" placeholder="秒杀价/分" /><input v-model.number="saleForm.totalStock" class="field" type="number" placeholder="活动库存" /></div><input v-model="saleForm.startsAt" class="field" type="datetime-local" /><input v-model="saleForm.endsAt" class="field" type="datetime-local" /><button class="btn yellow" @click="addSale">OPEN LIMITED DROP</button><h3 class="sub-panel-title">COUPON TEMPLATE</h3><input v-model="couponForm.name" class="field" placeholder="券名称" /><div class="inline-fields"><input v-model.number="couponForm.thresholdCent" class="field" type="number" placeholder="门槛/分" /><input v-model.number="couponForm.discountCent" class="field" type="number" placeholder="优惠/分" /><input v-model.number="couponForm.totalStock" class="field" type="number" placeholder="数量" /></div><input v-model="couponForm.startsAt" class="field" type="datetime-local" /><input v-model="couponForm.endsAt" class="field" type="datetime-local" /><button class="btn ghost" @click="addCoupon">ISSUE COUPON</button></section>
    </div>
    <div class="commerce-grid">
      <section class="panel"><h3>MY ORDERS</h3><div class="job-list"><div v-for="o in orders" :key="o.id" class="job order-job"><div><b>{{ o.productName }}</b><small>{{ money(o.payableAmountCent) }} · {{ o.status }} · ORDER {{ o.id }}</small></div><div class="order-actions"><button v-if="o.status === 'PENDING_PAYMENT'" class="btn small yellow" @click="act(() => payOrder(o.id), '模拟支付成功')">PAY</button><button v-if="o.status === 'PENDING_PAYMENT'" class="btn small ghost" @click="act(() => cancelOrder(o.id), '订单已取消')">CANCEL</button><button v-if="o.status === 'SHIPPED'" class="btn small" @click="act(() => completeOrder(o.id), '已确认收货')">RECEIVE</button><button v-if="['PAID','SHIPPED','COMPLETED','REFUND_REJECTED'].includes(o.status)" class="btn small ghost" @click="refund(o)">REFUND</button></div></div><div v-if="!orders.length" class="empty">暂无购买记录</div></div><h4>MY COUPONS</h4><div class="coupon-list"><span v-for="c in coupons" :key="c.id" class="coupon-chip">{{ c.name }} · {{ c.status }} · -{{ money(c.discountCent) }}</span></div><h4>MY AFTER-SALES</h4><div class="job-list"><div v-for="r in refunds" :key="r.id" class="job"><div><b>ORDER {{ r.orderId }}</b><small>{{ r.reason }} · {{ r.reply || '等待处理' }}</small></div><span class="badge">{{ r.status }}</span></div></div></section>
      <section class="panel"><h3>CREATOR // FULFILLMENT</h3><div class="job-list"><div v-for="o in creatorOrders" :key="o.id" class="job order-job"><div><b>{{ o.productName }}</b><small>{{ o.status }} · ORDER {{ o.id }}</small></div><button v-if="o.status === 'PAID'" class="btn small" @click="act(() => shipOrder(o.id), '订单已发货')">SHIP</button></div><div v-if="!creatorOrders.length" class="empty">暂无创作者订单</div></div><h4>REFUND REVIEW</h4><div class="job-list"><div v-for="r in creatorRefunds" :key="r.id" class="job order-job"><div><b>ORDER {{ r.orderId }}</b><small>{{ r.reason }} · {{ r.status }}</small></div><div v-if="r.status === 'PENDING'" class="order-actions"><button class="btn small" @click="act(() => reviewRefund(r.id, true, '同意退款'), '退款已同意')">APPROVE</button><button class="btn small ghost" @click="act(() => reviewRefund(r.id, false, '不符合退款条件'), '退款已拒绝')">REJECT</button></div></div></div></section>
    </div>
  </section>
</template>
