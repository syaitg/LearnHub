import request from "@/utils/request.js"

const MESSAGE_API_PREFIX = "/sms"

export const getInboxPage = (params) => request({ url: `${MESSAGE_API_PREFIX}/inboxes`, method: "get", params })
export const markInboxRead = (id) => request({ url: `${MESSAGE_API_PREFIX}/inboxes/${id}/read`, method: "put" })
export const markAllInboxRead = () => request({ url: `${MESSAGE_API_PREFIX}/inboxes/read-all`, method: "put" })
export const getUnreadInboxCount = () => request({ url: `${MESSAGE_API_PREFIX}/inboxes/unread-count`, method: "get" })
