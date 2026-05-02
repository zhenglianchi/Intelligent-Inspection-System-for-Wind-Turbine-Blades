/*
 Navicat Premium Data Transfer

 Source Server         : LocalMySQL
 Source Server Type    : MySQL
 Source Server Version : 50738
 Source Host           : localhost:3306
 Source Schema         : healthmonitor

 Target Server Type    : MySQL
 Target Server Version : 50738
 File Encoding         : 65001

 Date: 18/05/2023 15:52:34
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for hm_realtime
-- ----------------------------
DROP TABLE IF EXISTS `hm_realtime`;
CREATE TABLE `hm_realtime`  (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `windturbine` int(11) NOT NULL,
  `windfarm` varchar(11) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL,
  `status` int(11) NOT NULL,
  `feature1` double NOT NULL,
  `feature2` double NOT NULL,
  `feature3` double NOT NULL,
  `gmt_received` datetime NOT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 216 CHARACTER SET = utf8 COLLATE = utf8_general_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of hm_realtime
-- ----------------------------
INSERT INTO `hm_realtime` VALUES (1, 1, '10001', 1, 1.5, 1.5, 1.5, '2023-02-20 10:48:54');
INSERT INTO `hm_realtime` VALUES (2, 2, '10001', 0, 1.5, 1.5, 1.5, '2023-02-20 10:48:54');
INSERT INTO `hm_realtime` VALUES (3, 1, '10002', 1, 1.5, 1.5, 1.5, '2023-02-20 10:48:54');
INSERT INTO `hm_realtime` VALUES (4, 1, '10003', 1, 1.5, 1.5, 1.5, '2023-02-20 10:48:54');
INSERT INTO `hm_realtime` VALUES (5, 1, '20001', 1, 1.5, 1.5, 1.5, '2023-02-20 10:48:54');
INSERT INTO `hm_realtime` VALUES (6, 1, '20002', 1, 1.5, 1.5, 1.5, '2023-02-20 14:53:48');
INSERT INTO `hm_realtime` VALUES (7, 1, '10001', 1, 1.5, 1.5, 1.5, '2023-02-20 17:23:24');
INSERT INTO `hm_realtime` VALUES (8, 1, '10001', 1, 1.5, 1.5, 1.5, '2023-02-20 17:23:41');
INSERT INTO `hm_realtime` VALUES (9, 3, '10001', 1, 1.5, 1.5, 1.5, '2023-02-20 17:23:57');
INSERT INTO `hm_realtime` VALUES (10, 5, '10001', 1, 1.5, 1.5, 1.5, '2023-02-20 17:24:02');
INSERT INTO `hm_realtime` VALUES (11, 11, '10001', 1, 1.5, 1.5, 1.5, '2023-02-20 17:23:41');
INSERT INTO `hm_realtime` VALUES (12, 11, '10001', 1, 1.5, 1.5, 1.5, '2023-02-20 09:23:41');
INSERT INTO `hm_realtime` VALUES (13, 11, '10001', 1, 1.5, 1.5, 1.5, '2023-02-20 09:23:41');
INSERT INTO `hm_realtime` VALUES (14, 11, '10001', 1, 1.5, 1.5, 1.5, '2023-02-20 09:23:41');
INSERT INTO `hm_realtime` VALUES (15, 11, '10001', 1, 1.5, 1.5, 1.5, '2023-02-20 09:23:41');
INSERT INTO `hm_realtime` VALUES (16, 11, '10001', 1, 1.5, 1.5, 1.5, '2023-02-20 09:23:41');
INSERT INTO `hm_realtime` VALUES (17, 16, '10001', 1, 1.5, 1.5, 1.5, '2023-02-20 09:23:41');
INSERT INTO `hm_realtime` VALUES (18, 14, '10001', 1, 1.5, 1.5, 1.5, '2023-02-20 09:23:41');
INSERT INTO `hm_realtime` VALUES (19, 14, '10001', 1, 1.5, 1.5, 1.5, '2023-02-20 09:23:41');
INSERT INTO `hm_realtime` VALUES (20, 15, '10001', 1, 1.5, 1.5, 1.5, '2023-02-20 09:23:41');
INSERT INTO `hm_realtime` VALUES (21, 17, '10001', 1, 1.5, 1.5, 1.5, '2023-02-20 09:23:41');
INSERT INTO `hm_realtime` VALUES (22, 17, '10001', 1, 1.5, 1.5, 1.5, '2023-02-20 09:23:41');
INSERT INTO `hm_realtime` VALUES (23, 18, '10001', 1, 1.5, 1.5, 1.5, '2023-02-20 09:23:41');
INSERT INTO `hm_realtime` VALUES (24, 18, '10001', 1, 1.5, 1.5, 1.5, '2023-02-20 09:23:41');
INSERT INTO `hm_realtime` VALUES (25, 19, '10001', 1, 1.5, 1.5, 1.5, '2023-02-20 09:23:41');
INSERT INTO `hm_realtime` VALUES (26, 19, '10001', 1, 1.5, 1.5, 1.5, '2023-02-20 09:23:41');
INSERT INTO `hm_realtime` VALUES (27, 19, '10001', 1, 1.5, 1.5, 1.5, '2023-02-20 09:23:41');
INSERT INTO `hm_realtime` VALUES (28, 1, '10001', 1, 1.5, 1.5, 1.5, '2023-02-20 10:48:54');
INSERT INTO `hm_realtime` VALUES (29, 1, '10001', 1, 1.5, 1.5, 1.5, '2023-02-20 10:48:54');
INSERT INTO `hm_realtime` VALUES (30, 1, '10001', 1, 1.5, 1.5, 1.5, '2023-02-20 10:48:54');
INSERT INTO `hm_realtime` VALUES (31, 1, '10001', 1, 1.5, 1.5, 1.5, '2023-02-20 10:48:54');
INSERT INTO `hm_realtime` VALUES (32, 1, '10001', 1, 1.5, 1.5, 1.5, '2023-02-20 10:48:54');
INSERT INTO `hm_realtime` VALUES (33, 1, '10001', 1, 1.5, 1.5, 1.5, '2023-02-20 10:48:54');
INSERT INTO `hm_realtime` VALUES (34, 1, '10001', 1, 1.5, 1.5, 1.5, '2023-02-20 10:48:54');
INSERT INTO `hm_realtime` VALUES (35, 1, '10001', 1, 1.5, 1.5, 1.5, '2023-02-20 10:48:54');
INSERT INTO `hm_realtime` VALUES (36, 1, '10001', 1, 1.5, 1.5, 1.5, '2023-02-20 10:48:54');
INSERT INTO `hm_realtime` VALUES (37, 1, '10001', 1, 1.5, 1.5, 1.5, '2023-02-22 16:48:52');
INSERT INTO `hm_realtime` VALUES (38, 2, '10001', 1, 1.5, 1.5, 1.5, '2023-02-22 17:03:24');
INSERT INTO `hm_realtime` VALUES (39, 1, '10001', 1, 1.5, 1.5, 1.5, '2023-02-22 17:04:06');
INSERT INTO `hm_realtime` VALUES (40, 1, '10001', 1, 1.5, 1.5, 1.6, '2023-02-22 12:15:41');
INSERT INTO `hm_realtime` VALUES (41, 1, '10001', 1, 1.5, 1.5, 1.6, '2023-02-22 13:15:41');
INSERT INTO `hm_realtime` VALUES (42, 1, '10001', 1, 1.5, 1.5, 1.6, '2023-02-22 13:15:41');
INSERT INTO `hm_realtime` VALUES (43, 1, '10001', 1, 1.5, 1.5, 1.6, '2023-02-22 03:15:41');
INSERT INTO `hm_realtime` VALUES (44, 1, '10001', 1, 1.5, 1.5, 1.6, '2023-02-22 20:21:41');
INSERT INTO `hm_realtime` VALUES (45, 1, '10001', 1, 1.5, 1.5, 1, '2023-02-22 20:23:41');
INSERT INTO `hm_realtime` VALUES (46, 1, '10001', 1, 1.5, 1.5, 1, '2023-02-22 12:34:41');
INSERT INTO `hm_realtime` VALUES (47, 1, '10001', 1, 1.5, 1.5, 1, '2023-02-22 12:34:41');
INSERT INTO `hm_realtime` VALUES (48, 1, '10001', 1, 1.5, 1.5, 1, '2023-02-22 12:34:41');
INSERT INTO `hm_realtime` VALUES (49, 1, '10001', 1, 1.5, 1.5, 1, '2023-02-22 12:34:41');
INSERT INTO `hm_realtime` VALUES (50, 1, '10001', 1, 1.5, 1.5, 1.1, '2023-02-22 12:34:41');
INSERT INTO `hm_realtime` VALUES (51, 1, '10001', 1, 1.5, 1.5, 1.1, '2023-02-22 20:34:41');
INSERT INTO `hm_realtime` VALUES (52, 1, '10001', 1, 1.5, 1.5, 1.1, '2023-02-22 20:34:41');
INSERT INTO `hm_realtime` VALUES (53, 1, '10001', 1, 1.5, 1.5, 1.1, '2023-02-20 17:23:41');
INSERT INTO `hm_realtime` VALUES (54, 1, '10001', 1, 1.5, 1.5, 1.1, '2023-02-23 10:10:02');
INSERT INTO `hm_realtime` VALUES (55, 1, '10001', 1, 1.5, 1.5, 1.1, '2023-02-23 10:11:16');
INSERT INTO `hm_realtime` VALUES (56, 1, '10001', 1, 1.5, 1.5, 1.1, '2023-02-23 10:12:01');
INSERT INTO `hm_realtime` VALUES (57, 1, '10001', 1, 1.5, 1.5, 1.1, '2023-02-23 10:25:21');
INSERT INTO `hm_realtime` VALUES (58, 1, '10001', 1, 1.5, 1.5, 1.1, '2023-02-23 10:30:32');
INSERT INTO `hm_realtime` VALUES (59, 1, '10001', 1, 1.5, 1.5, 1.1, '2023-02-20 17:23:41');
INSERT INTO `hm_realtime` VALUES (60, 1, '10001', 1, 1.5, 1.5, 1.7, '2023-02-20 17:23:41');
INSERT INTO `hm_realtime` VALUES (61, 1, '10001', 1, 1.5, 1.5, 1.7, '2023-02-20 17:23:41');
INSERT INTO `hm_realtime` VALUES (62, 1, '10001', 1, 1.5, 1.5, 1, '2023-02-20 17:23:41');
INSERT INTO `hm_realtime` VALUES (63, 1, '10001', 1, 1.5, 1.5, 1, '2023-02-21 01:23:41');
INSERT INTO `hm_realtime` VALUES (64, 1, '10001', 1, 1.4, 1.4, 1.4, '2023-02-21 01:23:41');
INSERT INTO `hm_realtime` VALUES (65, 1, '10001', 1, 1.4, 1.4, 1.4, '2023-02-20 17:23:41');
INSERT INTO `hm_realtime` VALUES (66, 1, '10001', 1, 1.4, 1.4, 1.4, '2023-02-21 01:23:41');
INSERT INTO `hm_realtime` VALUES (67, 1, '10001', 1, 1.4, 1.4, 1.4, '2023-02-21 01:23:41');
INSERT INTO `hm_realtime` VALUES (68, 1, '10001', 1, 1.4, 1.4, 1.4, '2023-02-21 01:23:41');
INSERT INTO `hm_realtime` VALUES (69, 1, '10001', 1, 1.4, 1.4, 1.4, '2023-02-21 01:23:41');
INSERT INTO `hm_realtime` VALUES (70, 1, '10001', 1, 1.4, 1.4, 1.4, '2023-02-20 17:23:41');
INSERT INTO `hm_realtime` VALUES (71, 1, '10001', 1, 1.5, 1.5, 1.5, '2023-03-21 16:26:31');
INSERT INTO `hm_realtime` VALUES (72, 1, '10001', 1, 1.5, 1.5, 1.5, '2023-03-21 16:26:42');
INSERT INTO `hm_realtime` VALUES (73, 1, '10001', 1, 1.5, 1.5, 1.5, '2023-03-21 16:26:43');
INSERT INTO `hm_realtime` VALUES (74, 1, '10001', 1, 1.5, 1.5, 1.5, '2023-03-21 16:26:43');
INSERT INTO `hm_realtime` VALUES (75, 1, '10001', 1, 1.5, 1.5, 1.5, '2023-03-21 16:26:44');
INSERT INTO `hm_realtime` VALUES (76, 1, '10001', 1, 1.5, 1.5, 1.5, '2023-03-21 16:26:44');
INSERT INTO `hm_realtime` VALUES (77, 1, '10001', 1, 1.5, 1.5, 1.5, '2023-03-21 16:26:44');
INSERT INTO `hm_realtime` VALUES (78, 1, '10001', 1, 1.5, 1.5, 1.5, '2023-03-21 16:26:44');
INSERT INTO `hm_realtime` VALUES (79, 1, '10001', 1, 1.5, 1.5, 1.5, '2023-03-21 16:26:44');
INSERT INTO `hm_realtime` VALUES (80, 1, '10001', 1, 1.5, 1.5, 1.5, '2023-03-21 16:26:44');
INSERT INTO `hm_realtime` VALUES (81, 1, '10001', 1, 1.5, 1.5, 1.5, '2023-03-21 16:26:44');
INSERT INTO `hm_realtime` VALUES (82, 1, '10001', 1, 1.5, 1.5, 1.5, '2023-03-21 16:26:44');
INSERT INTO `hm_realtime` VALUES (83, 1, '10001', 1, 1.5, 1.5, 1.5, '2023-03-21 16:26:45');
INSERT INTO `hm_realtime` VALUES (84, 1, '10001', 1, 1.5, 1.5, 1.5, '2023-03-21 16:26:45');
INSERT INTO `hm_realtime` VALUES (85, 1, '10001', 1, 1.5, 1.5, 1.5, '2023-03-21 16:26:45');
INSERT INTO `hm_realtime` VALUES (86, 1, '10001', 1, 1.5, 1.5, 1.5, '2023-03-21 16:26:45');
INSERT INTO `hm_realtime` VALUES (87, 1, '10001', 1, 1.5, 1.5, 1.5, '2023-03-21 16:26:45');
INSERT INTO `hm_realtime` VALUES (88, 3, '10001', 1, 1.5, 1.5, 1.5, '2023-03-21 16:27:53');
INSERT INTO `hm_realtime` VALUES (89, 0, '10001', 1, 1.5, 1.5, 1.5, '2023-03-21 16:28:01');
INSERT INTO `hm_realtime` VALUES (90, 9, '10001', 1, 1.5, 1.5, 1.5, '2023-03-21 16:28:13');
INSERT INTO `hm_realtime` VALUES (91, 9, '10001', 1, 1.5, 1.5, 1.5, '2023-03-21 21:00:46');
INSERT INTO `hm_realtime` VALUES (92, 1, '10001', 1, 1.4, 1.4, 1.4, '2023-02-20 17:23:41');
INSERT INTO `hm_realtime` VALUES (93, 1, '10001', 1, 1.4, 1.4, 1.4, '2023-02-20 17:23:41');
INSERT INTO `hm_realtime` VALUES (94, 1, '10001', 1, 1.4, 1.4, 1.4, '2023-02-20 17:23:41');
INSERT INTO `hm_realtime` VALUES (95, 1, '10001', 1, 1.4, 1.4, 1.4, '2023-03-01 17:23:41');
INSERT INTO `hm_realtime` VALUES (96, 1, '10001', 1, 1.4, 1.4, 1.4, '2023-03-01 17:23:41');
INSERT INTO `hm_realtime` VALUES (97, 1, '10001', 1, 1.4, 1.4, 1.4, '2023-03-01 17:23:41');
INSERT INTO `hm_realtime` VALUES (98, 1, '10001', 1, 1.4, 1.4, 1.4, '2023-02-20 17:23:41');
INSERT INTO `hm_realtime` VALUES (99, 1, '10001', 1, 1.4, 1.4, 1.4, '2023-03-10 17:23:41');
INSERT INTO `hm_realtime` VALUES (100, 9, '10001', 1, 1.5, 1.5, 1, '2023-03-29 09:58:44');
INSERT INTO `hm_realtime` VALUES (101, 1, '10001', 1, 1.5, 1.5, 1, '2023-03-29 09:59:25');
INSERT INTO `hm_realtime` VALUES (102, 1, '10001', 0, 1.5, 1.5, 1, '2023-04-06 20:27:29');
INSERT INTO `hm_realtime` VALUES (103, 1, '10001', 1, 1.5, 1.5, 1, '2023-05-03 10:53:19');
INSERT INTO `hm_realtime` VALUES (104, 1, '10001', 1, 1.5, 1.5, 1, '2023-05-03 11:29:02');
INSERT INTO `hm_realtime` VALUES (105, 1, '10001', 2, 1.5, 1.5, 1, '2023-05-03 11:29:19');
INSERT INTO `hm_realtime` VALUES (106, 1, '10001', 2, 1.5, 1.5, 1, '2023-05-03 14:57:56');
INSERT INTO `hm_realtime` VALUES (107, 1, '10001', 2, 1.5, 1.5, 1, '2023-05-03 14:58:14');
INSERT INTO `hm_realtime` VALUES (108, 1, '10001', 1, 1.5, 1.5, 1, '2023-05-03 14:58:19');
INSERT INTO `hm_realtime` VALUES (109, 1, '10001', 2, 1.5, 1.5, 1, '2023-05-03 14:58:26');
INSERT INTO `hm_realtime` VALUES (110, 1, '10001', 1, 1.5, 1.5, 1, '2023-05-03 14:58:40');
INSERT INTO `hm_realtime` VALUES (111, 1, '10001', 1, 1.5, 1.5, 1, '2023-05-03 15:01:04');
INSERT INTO `hm_realtime` VALUES (112, 1, '10001', 2, 1.5, 1.5, 1, '2023-05-03 15:01:09');
INSERT INTO `hm_realtime` VALUES (113, 1, '10001', 2, 1.5, 1.5, 1, '2023-05-03 15:01:16');
INSERT INTO `hm_realtime` VALUES (114, 1, '10001', 2, 1.5, 1.5, 1, '2023-05-03 15:03:32');
INSERT INTO `hm_realtime` VALUES (115, 1, '10001', 1, 1.5, 1.5, 1, '2023-05-03 15:03:39');
INSERT INTO `hm_realtime` VALUES (116, 1, '10001', 2, 1.5, 1.5, 1, '2023-05-03 15:03:48');
INSERT INTO `hm_realtime` VALUES (117, 1, '10001', 1, 1.5, 1.5, 1, '2023-05-03 15:04:47');
INSERT INTO `hm_realtime` VALUES (118, 1, '10001', 2, 1.5, 1.5, 1, '2023-05-03 15:05:33');
INSERT INTO `hm_realtime` VALUES (119, 1, '10001', 1, 1.5, 1.5, 1, '2023-05-03 15:11:12');
INSERT INTO `hm_realtime` VALUES (120, 1, '10001', 0, 1.5, 1.5, 1, '2023-05-03 15:11:34');
INSERT INTO `hm_realtime` VALUES (121, 1, '10001', 1, 1.5, 1.5, 1, '2023-05-03 15:12:05');
INSERT INTO `hm_realtime` VALUES (122, 1, '10001', 1, 1.5, 1.5, 1, '2023-05-03 15:12:15');
INSERT INTO `hm_realtime` VALUES (123, 1, '10001', 1, 1.5, 1.5, 1, '2023-05-04 09:46:38');
INSERT INTO `hm_realtime` VALUES (124, 1, '10001', 0, 1.5, 1.5, 1, '2023-05-04 09:46:52');
INSERT INTO `hm_realtime` VALUES (125, 1, '10001', 1, 1.5, 1.5, 1, '2023-05-04 09:47:55');
INSERT INTO `hm_realtime` VALUES (126, 1, '10001', 0, 1.5, 1.5, 1, '2023-05-04 09:48:01');
INSERT INTO `hm_realtime` VALUES (127, 1, '10001', 1, 1.5, 1.5, 1, '2023-05-04 09:48:06');
INSERT INTO `hm_realtime` VALUES (128, 1, '10001', 1, 1.5, 1.5, 1, '2023-05-04 09:48:41');
INSERT INTO `hm_realtime` VALUES (129, 1, '10001', 1, 1.5, 1.5, 1, '2023-05-04 14:44:22');
INSERT INTO `hm_realtime` VALUES (130, 1, '10001', 1, 1.5, 1.5, 1, '2023-05-04 14:44:45');
INSERT INTO `hm_realtime` VALUES (131, 1, '10001', 1, 1.5, 1.5, 1, '2023-05-04 14:47:40');
INSERT INTO `hm_realtime` VALUES (132, 1, '10001', 1, 1.5, 1.5, 1, '2023-05-04 14:48:20');
INSERT INTO `hm_realtime` VALUES (133, 1, '10001', 0, 1.5, 1.5, 1, '2023-05-04 14:48:26');
INSERT INTO `hm_realtime` VALUES (134, 1, '10001', 1, 1.5, 1.5, 1, '2023-05-04 14:48:58');
INSERT INTO `hm_realtime` VALUES (135, 1, '10001', 1, 1.5, 1.5, 3, '2023-05-04 14:57:25');
INSERT INTO `hm_realtime` VALUES (136, 1, '10001', 1, 1.5, 1.5, 3, '2023-05-04 14:57:40');
INSERT INTO `hm_realtime` VALUES (137, 1, '10001', 1, 1.5, 1.5, 3, '2023-05-04 14:57:41');
INSERT INTO `hm_realtime` VALUES (138, 1, '10001', 1, 1.5, 1.5, 3, '2023-05-04 14:57:41');
INSERT INTO `hm_realtime` VALUES (139, 1, '10001', 1, 1.5, 1.5, 3, '2023-05-04 14:57:41');
INSERT INTO `hm_realtime` VALUES (140, 1, '10001', 1, 1.5, 1.5, 3, '2023-05-04 14:57:41');
INSERT INTO `hm_realtime` VALUES (141, 1, '10001', 1, 1.5, 1.5, 3, '2023-05-04 14:57:41');
INSERT INTO `hm_realtime` VALUES (142, 1, '10001', 1, 1.5, 1.5, 3, '2023-05-04 14:57:41');
INSERT INTO `hm_realtime` VALUES (143, 1, '10001', 1, 1.5, 1.5, 3, '2023-05-04 14:57:41');
INSERT INTO `hm_realtime` VALUES (144, 1, '10001', 1, 1.5, 1.5, 3, '2023-05-04 14:57:42');
INSERT INTO `hm_realtime` VALUES (145, 1, '10001', 1, 1.5, 1.5, 3, '2023-05-04 14:57:42');
INSERT INTO `hm_realtime` VALUES (146, 1, '10001', 1, 1.5, 1.5, 3, '2023-05-04 14:58:13');
INSERT INTO `hm_realtime` VALUES (147, 1, '10001', 1, 0, 0, 1, '2023-05-04 15:08:41');
INSERT INTO `hm_realtime` VALUES (148, 1, '10001', 1, 0, 0, 3, '2023-05-04 15:08:50');
INSERT INTO `hm_realtime` VALUES (149, 1, '10001', 0, 0, 0, 3, '2023-05-04 15:08:56');
INSERT INTO `hm_realtime` VALUES (150, 1, '10001', 0, 0, 0, 1, '2023-05-04 15:09:29');
INSERT INTO `hm_realtime` VALUES (151, 1, '10001', 1, 0, 0, 1, '2023-05-04 15:09:35');
INSERT INTO `hm_realtime` VALUES (152, 1, '10001', 1, 0, 0, 1, '2023-05-09 09:14:07');
INSERT INTO `hm_realtime` VALUES (153, 1, '10001', 1, 0, 0, 1, '2023-05-09 09:14:31');
INSERT INTO `hm_realtime` VALUES (154, 1, '10001', 2, 0, 0, 1, '2023-05-09 09:14:37');
INSERT INTO `hm_realtime` VALUES (155, 1, '10001', 1, 0, 0, 1, '2023-05-09 09:14:45');
INSERT INTO `hm_realtime` VALUES (156, 1, '10001', 2, 0, 0, 1, '2023-05-09 09:14:50');
INSERT INTO `hm_realtime` VALUES (157, 1, '10001', 2, 0, 0, 1, '2023-05-09 09:14:51');
INSERT INTO `hm_realtime` VALUES (158, 1, '10001', 1, 0, 0, 1, '2023-05-09 09:16:36');
INSERT INTO `hm_realtime` VALUES (159, 1, '10001', 1, 0, 0, 1, '2023-05-09 09:17:26');
INSERT INTO `hm_realtime` VALUES (160, 1, '10001', 2, 0, 0, 1, '2023-05-09 09:18:09');
INSERT INTO `hm_realtime` VALUES (161, 1, '10001', 1, 0, 0, 1, '2023-05-09 09:18:59');
INSERT INTO `hm_realtime` VALUES (162, 1, '10001', 0, 0, 0, 1, '2023-05-09 09:19:08');
INSERT INTO `hm_realtime` VALUES (163, 1, '10001', 0, 0, 0, 1, '2023-05-09 09:19:53');
INSERT INTO `hm_realtime` VALUES (164, 1, '10001', 1, 0, 0, 1, '2023-05-09 09:20:14');
INSERT INTO `hm_realtime` VALUES (165, 1, '10001', 1, 0, 0, 1, '2023-05-09 09:20:45');
INSERT INTO `hm_realtime` VALUES (166, 1, '10001', 0, 0, 0, 1, '2023-05-09 09:20:50');
INSERT INTO `hm_realtime` VALUES (167, 1, '10001', 1, 0, 0, 1, '2023-05-09 09:21:08');
INSERT INTO `hm_realtime` VALUES (168, 1, '10001', 0, 0, 0, 1, '2023-05-09 09:21:13');
INSERT INTO `hm_realtime` VALUES (169, 1, '10001', 0, 0, 0, 1, '2023-05-09 09:21:54');
INSERT INTO `hm_realtime` VALUES (170, 1, '10001', 0, 0, 0, 1, '2023-05-09 09:26:20');
INSERT INTO `hm_realtime` VALUES (171, 1, '10001', 1, 0, 0, 1, '2023-05-09 09:26:25');
INSERT INTO `hm_realtime` VALUES (172, 1, '10001', 0, 0, 0, 1, '2023-05-09 09:26:40');
INSERT INTO `hm_realtime` VALUES (173, 1, '10001', 0, 0, 0, 1, '2023-05-09 09:26:51');
INSERT INTO `hm_realtime` VALUES (174, 1, '10001', 1, 0, 0, 1, '2023-05-09 09:26:56');
INSERT INTO `hm_realtime` VALUES (175, 1, '10001', 1, 0, 0, 2, '2023-05-09 14:52:15');
INSERT INTO `hm_realtime` VALUES (176, 1, '10001', 1, 0, 0, 2, '2023-05-09 15:01:07');
INSERT INTO `hm_realtime` VALUES (177, 1, '10001', 1, 0, 0, 2, '2023-05-09 15:01:13');
INSERT INTO `hm_realtime` VALUES (178, 1, '10001', 1, 0, 0, 2, '2023-05-09 15:17:31');
INSERT INTO `hm_realtime` VALUES (179, 1, '10001', 1, 0, 0, 2, '2023-05-09 15:18:20');
INSERT INTO `hm_realtime` VALUES (180, 1, '10001', 1, 0, 0, 2, '2023-05-09 15:20:18');
INSERT INTO `hm_realtime` VALUES (181, 1, '10001', 1, 0, 0, 2, '2023-05-09 15:20:45');
INSERT INTO `hm_realtime` VALUES (182, 1, '10001', 1, 0, 0, 2, '2023-05-09 15:21:54');
INSERT INTO `hm_realtime` VALUES (183, 1, '10001', 1, 0, 0, 2, '2023-05-09 15:22:18');
INSERT INTO `hm_realtime` VALUES (184, 1, '10001', 1, 0, 0, 2, '2023-05-09 15:25:55');
INSERT INTO `hm_realtime` VALUES (185, 1, '10001', 1, 0, 0, 2, '2023-05-09 15:27:51');
INSERT INTO `hm_realtime` VALUES (186, 1, '10001', 1, 0, 0, 2, '2023-05-09 15:28:15');
INSERT INTO `hm_realtime` VALUES (187, 1, '10001', 0, 0, 0, 2, '2023-05-09 15:28:22');
INSERT INTO `hm_realtime` VALUES (188, 1, '10001', 0, 0, 0, 1, '2023-05-09 15:31:55');
INSERT INTO `hm_realtime` VALUES (189, 2, '10001', 0, 0, 0, 1, '2023-05-09 15:32:05');
INSERT INTO `hm_realtime` VALUES (190, 1, '10001', 0, 0, 0, 1, '2023-05-09 15:32:17');
INSERT INTO `hm_realtime` VALUES (191, 2, '10001', 0, 0, 0, 1, '2023-05-09 15:44:18');
INSERT INTO `hm_realtime` VALUES (192, 2, '10001', 0, 0, 0, 1, '2023-05-09 15:44:23');
INSERT INTO `hm_realtime` VALUES (193, 2, '10001', 0, 0, 0, 1, '2023-05-09 15:44:26');
INSERT INTO `hm_realtime` VALUES (194, 2, '10001', 0, 0, 0, 1, '2023-05-09 15:44:29');
INSERT INTO `hm_realtime` VALUES (195, 1, '10001', 0, 0, 0, 1, '2023-05-09 15:44:40');
INSERT INTO `hm_realtime` VALUES (196, 1, '10001', 0, 0, 0, 1, '2023-05-09 15:45:03');
INSERT INTO `hm_realtime` VALUES (197, 1, '10001', 0, 0, 0, 1, '2023-05-09 15:45:07');
INSERT INTO `hm_realtime` VALUES (198, 2, '10001', 0, 0, 0, 1, '2023-05-09 15:45:18');
INSERT INTO `hm_realtime` VALUES (199, 2, '10001', 0, 0, 0, 1, '2023-05-09 15:46:17');
INSERT INTO `hm_realtime` VALUES (200, 1, '10001', 0, 0, 0, 1, '2023-05-09 15:46:47');
INSERT INTO `hm_realtime` VALUES (201, 2, '10001', 0, 0, 0, 1, '2023-05-09 15:47:03');
INSERT INTO `hm_realtime` VALUES (202, 1, '10001', 0, 0, 0, 1, '2023-05-09 15:47:43');
INSERT INTO `hm_realtime` VALUES (203, 1, '10001', 0, 0, 0, 1, '2023-05-09 16:01:48');
INSERT INTO `hm_realtime` VALUES (204, 1, '10001', 0, 0, 0, 1, '2023-05-09 16:03:14');
INSERT INTO `hm_realtime` VALUES (205, 1, '10001', 0, 0, 0, 1, '2023-05-09 16:04:33');
INSERT INTO `hm_realtime` VALUES (206, 1, '10001', 0, 0, 0, 1, '2023-05-09 16:05:27');
INSERT INTO `hm_realtime` VALUES (207, 1, '10001', 0, 0, 0, 1, '2023-05-09 16:08:00');
INSERT INTO `hm_realtime` VALUES (208, 1, '10001', 0, 0, 0, 1, '2023-05-09 16:08:28');
INSERT INTO `hm_realtime` VALUES (209, 1, '10001', 0, 0, 0, 1, '2023-05-09 16:08:57');
INSERT INTO `hm_realtime` VALUES (210, 2, '10001', 0, 0, 0, 1, '2023-05-09 16:09:13');
INSERT INTO `hm_realtime` VALUES (211, 1, '10001', 1, 0, 0, 1, '2023-05-09 16:09:23');
INSERT INTO `hm_realtime` VALUES (212, 1, '10001', 1, 0, 0, 1, '2023-05-09 16:09:55');
INSERT INTO `hm_realtime` VALUES (213, 1, '10001', 0, 0, 0, 1, '2023-05-09 16:10:01');
INSERT INTO `hm_realtime` VALUES (214, 2, '10001', 0, 0, 0, 1, '2023-05-09 16:10:18');
INSERT INTO `hm_realtime` VALUES (215, 1, '10001', 0, 1, 1, 1, '2023-05-15 09:50:04');

-- ----------------------------
-- Table structure for hm_region
-- ----------------------------
DROP TABLE IF EXISTS `hm_region`;
CREATE TABLE `hm_region`  (
  `id` int(11) NOT NULL,
  `name` varchar(50) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL,
  `longitude` double NOT NULL,
  `latitude` double NOT NULL,
  `map_level` int(11) NOT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8 COLLATE = utf8_general_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of hm_region
-- ----------------------------
INSERT INTO `hm_region` VALUES (1, '华北地区', 115, 37, 7);
INSERT INTO `hm_region` VALUES (2, '东北地区', 127.5, 47, 7);
INSERT INTO `hm_region` VALUES (3, '华东地区', 119, 33, 7);
INSERT INTO `hm_region` VALUES (4, '华南地区', 110.75, 22.25, 7);

-- ----------------------------
-- Table structure for hm_user
-- ----------------------------
DROP TABLE IF EXISTS `hm_user`;
CREATE TABLE `hm_user`  (
  `id` int(11) NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `name` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '姓名',
  `sex` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '性别',
  `tel` varchar(11) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '电话',
  `age` int(11) NULL DEFAULT NULL COMMENT '年龄',
  `address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '地址',
  `user` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `pwd` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `position` int(255) NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `user`(`user`) USING BTREE,
  UNIQUE INDEX `user_2`(`user`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 15 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of hm_user
-- ----------------------------
INSERT INTO `hm_user` VALUES (9, 'aa', '男', '13025992199', 18, '北京海淀', 'as', 'asd', 1);
INSERT INTO `hm_user` VALUES (10, 'bb', '男', '13025992199', 18, '北京海淀', 'weqt', 'asd', 1);
INSERT INTO `hm_user` VALUES (14, 'lg', '', '', 0, '', 'lg', '123456', 0);

-- ----------------------------
-- Table structure for hm_windfarm_info
-- ----------------------------
DROP TABLE IF EXISTS `hm_windfarm_info`;
CREATE TABLE `hm_windfarm_info`  (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `windfarm` varchar(11) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL,
  `name` varchar(50) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL,
  `windturbine_count` int(11) NOT NULL,
  `province` varchar(255) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL,
  `region` int(255) NOT NULL,
  `unconnected_count` int(11) NOT NULL DEFAULT 0,
  `fault_count` int(11) NOT NULL,
  `health_count` int(11) NOT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 11 CHARACTER SET = utf8 COLLATE = utf8_general_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of hm_windfarm_info
-- ----------------------------
INSERT INTO `hm_windfarm_info` VALUES (1, '10001', '围场塞罕坝风场', 14, '河北', 1, 3, 9, 0);
INSERT INTO `hm_windfarm_info` VALUES (2, '10002', '尚义麒麟山风场', 14, '河北', 1, 0, 0, 0);
INSERT INTO `hm_windfarm_info` VALUES (3, '10003', '内蒙古锡林浩特风场', 14, '内蒙古', 1, 0, 0, 0);
INSERT INTO `hm_windfarm_info` VALUES (4, '10004', '承德红松洼风场', 13, '河北', 1, 1, 0, 0);
INSERT INTO `hm_windfarm_info` VALUES (5, '20001', '黑龙江依兰风场', 14, '黑龙江', 2, 0, 0, 0);
INSERT INTO `hm_windfarm_info` VALUES (6, '20002', '辽宁昌图风场', 14, '辽宁', 2, 0, 0, 0);
INSERT INTO `hm_windfarm_info` VALUES (7, '30001', '上海东大桥风场', 14, '上海', 3, 0, 1, 0);
INSERT INTO `hm_windfarm_info` VALUES (8, '30002', '浙江慈溪风场', 14, '浙江', 3, 0, 2, 0);
INSERT INTO `hm_windfarm_info` VALUES (9, '40001', '广东南澳风场', 13, '广东', 4, 0, 0, 0);
INSERT INTO `hm_windfarm_info` VALUES (10, '40002', '广东上川岛风场', 14, '广东', 4, 0, 0, 0);

-- ----------------------------
-- Table structure for hm_windturbine_info
-- ----------------------------
DROP TABLE IF EXISTS `hm_windturbine_info`;
CREATE TABLE `hm_windturbine_info`  (
  `id` int(11) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `windturbine` int(11) NULL DEFAULT NULL COMMENT '风机编号',
  `windfarm` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '风场编号',
  `status` int(11) NULL DEFAULT NULL COMMENT '健康状态 ',
  `gmt_create` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `gmt_modified` datetime NULL DEFAULT NULL COMMENT '修改时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of hm_windturbine_info
-- ----------------------------
INSERT INTO `hm_windturbine_info` VALUES (1, 1, '10001', 1, '2023-02-23 15:30:53', '2023-02-23 15:30:55');
INSERT INTO `hm_windturbine_info` VALUES (2, 2, '10001', 0, '2023-02-22 15:31:26', '2023-02-22 15:31:28');
INSERT INTO `hm_windturbine_info` VALUES (3, 3, '10001', 0, '2023-02-22 15:31:26', '2023-02-22 15:31:28');
INSERT INTO `hm_windturbine_info` VALUES (4, 4, '10001', 0, '2023-02-22 15:31:26', '2023-02-22 15:31:28');

SET FOREIGN_KEY_CHECKS = 1;
