## [1.0.2](https://github.com/madrigal-eschat/dev-events-jetbrains-publisher/compare/v1.0.1...v1.0.2) (2026-07-03)


### Bug Fixes

* register listeners under applicationListeners, correct EP/notification IDs ([870379b](https://github.com/madrigal-eschat/dev-events-jetbrains-publisher/commit/870379bac2b1ce057e80b2028a5989d863ba7bbf))

## [1.0.1](https://github.com/madrigal-eschat/dev-events-jetbrains-publisher/compare/v1.0.0...v1.0.1) (2026-07-02)


### Bug Fixes

* **settings:** strip domain from hostname in topic preview ([d1857b5](https://github.com/madrigal-eschat/dev-events-jetbrains-publisher/commit/d1857b58172c2295506b6102755a6de1c4127078))

# 1.0.0 (2026-06-30)


* feat!: rework to CloudEvents 1.0 message format ([c659a11](https://github.com/madrigal-eschat/dev-events-jetbrains-publisher/commit/c659a116cd5174cde1ffe8d040dded17c77297d4))


### Bug Fixes

* **ci:** correct repo name in cross-repo workflow references ([b1c04ef](https://github.com/madrigal-eschat/dev-events-jetbrains-publisher/commit/b1c04ef57372515129f30e6f1eb2d468210c9218))
* **ci:** grant required permissions for cross-repo semrel call ([b23d350](https://github.com/madrigal-eschat/dev-events-jetbrains-publisher/commit/b23d35082bd95ea21fdc01d301e88194b8e07517))
* **mqtt:** use stable product name for source.ide field ([f17f0c0](https://github.com/madrigal-eschat/dev-events-jetbrains-publisher/commit/f17f0c03e749fea5038b18b58ff488396f72424b))
* **plugin:** correct listener bus registration and topic interface FQNs ([359c8d7](https://github.com/madrigal-eschat/dev-events-jetbrains-publisher/commit/359c8d77616191177e861d2309e6ea5f409d24ab))
* **settings:** remove unimplemented vcs_push from ALL_EVENTS ([1e4c04d](https://github.com/madrigal-eschat/dev-events-jetbrains-publisher/commit/1e4c04d8035138693c69f92cf1fe39bb664d8853))


### Features

* add home subnet filter to suppress publishing on non-home networks ([f504568](https://github.com/madrigal-eschat/dev-events-jetbrains-publisher/commit/f504568f8bcc230c77b3b9a40910bd72e4d23d25))
* add Paho dependency, EventMode enum, strip placeholder scaffold ([9325ec2](https://github.com/madrigal-eschat/dev-events-jetbrains-publisher/commit/9325ec2408e1b006b7c779d73921b53506966f15))
* **listeners:** add breakpoint hit and application focus listeners ([0dd9f7a](https://github.com/madrigal-eschat/dev-events-jetbrains-publisher/commit/0dd9f7a01216aa2a8852c9c4be32bc2c96295e29))
* **listeners:** add build task and test run event listeners ([0479fb6](https://github.com/madrigal-eschat/dev-events-jetbrains-publisher/commit/0479fb61023eec3dbf43d4d1aef6e11ec0841b85))
* **listeners:** add file save, open, close event listeners ([aebdf9c](https://github.com/madrigal-eschat/dev-events-jetbrains-publisher/commit/aebdf9c345e9f1a1cb4b0c4d32e581b2343c76d0))
* **listeners:** add inspection complete listener ([59acf58](https://github.com/madrigal-eschat/dev-events-jetbrains-publisher/commit/59acf58406e4882db0b234147adedb4858a80cc1))
* **listeners:** add key presses listener with zero-transition detection ([5302763](https://github.com/madrigal-eschat/dev-events-jetbrains-publisher/commit/530276309b7265cebc670a5b2e4d9ab4cfd06983))
* **listeners:** add VCS commit, push, and branch change listeners ([140f3d3](https://github.com/madrigal-eschat/dev-events-jetbrains-publisher/commit/140f3d38ee83eef6ad2b95f925e86a0dbcbad4d3))
* **mqtt:** add MqttPublisherService with envelope builder ([119244b](https://github.com/madrigal-eschat/dev-events-jetbrains-publisher/commit/119244b3fc9acd87d7ca176f8d50b0d452eebf73))
* **settings:** add PluginSettings persistent state and password helpers ([9110bde](https://github.com/madrigal-eschat/dev-events-jetbrains-publisher/commit/9110bde9a1142930185cfee82729d5d0ee292b6b))
* **settings:** add Set All Off/Redacted/Full buttons to event mode table ([2fce21c](https://github.com/madrigal-eschat/dev-events-jetbrains-publisher/commit/2fce21cef3cc94f13bb4fd0b5b689dd9dba9729a))
* **settings:** add settings UI, configurable, and startup notification ([8d6ad6f](https://github.com/madrigal-eschat/dev-events-jetbrains-publisher/commit/8d6ad6f60d0033a67ea6e660b4599e858c2f01ec))


### BREAKING CHANGES

* message envelope schema has changed; any consumer
parsing the old format must be updated to CloudEvents 1.0

<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# DevEventsPublisher Changelog

## [Unreleased]
