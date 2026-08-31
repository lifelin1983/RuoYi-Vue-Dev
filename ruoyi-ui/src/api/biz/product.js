import request from '@/utils/request'

// 查询产品管理列表
export function listProduct(query) {
  return request({
    url: '/biz/product/list',
    method: 'get',
    params: query
  })
}

// 查询产品管理详细
export function getProduct(productId) {
  return request({
    url: '/biz/product/' + productId,
    method: 'get'
  })
}

// 新增产品管理
export function addProduct(data) {
  return request({
    url: '/biz/product',
    method: 'post',
    data: data
  })
}

// 修改产品管理
export function updateProduct(data) {
  return request({
    url: '/biz/product',
    method: 'put',
    data: data
  })
}

// 删除产品管理
export function delProduct(productId) {
  return request({
    url: '/biz/product/' + productId,
    method: 'delete'
  })
}
