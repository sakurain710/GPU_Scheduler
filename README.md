# A Heterogeneous GPU Computing Resource Scheduling System Based on Priority Queues

## Introduction
A system that allocates GPUs of different brands, models, and VRAM specifications to user-submitted tasks for simulation calculations

## Intelligent Task Scheduling Center:
- Uses Redis priority queues and a dynamic aging mechanism in the temporal dimension
- Uses the BestFit algorithm in the spatial dimension to reduce VRAM fragmentation waste
- Uses Redis distributed locks to ensure atomic decrements for concurrency control

## Task Execution Simulator:
GPU task execution employs Java’s ‘ExecutorService’ and ‘Future’ for asynchronous processing, with built-in timeout and circuit-breaker logic to simulate actual GPU task execution

## Heterogeneous Resource Management (Developing):
Real-time GPU hardware tracking, including recording, managing, and viewing GPU status information

## Permission Approval System (Current Status: Completed):
JWT stateless authentication + RBAC permission model

## Development Environment
- SpringBoot 3.5.11
    - Maven 3.9.11
    - Spring Security
    - Spring Web
    - Spring Validation
- Java 17
- MySQL 8.0
- mybatis-plus 3.5.12
- jjwt 0.12.6(api、impl、jackson)
- Database Connection Pool - Alibaba Druid 1.2.28
- Lombok 1.18.42
- Redis、Bucket4j