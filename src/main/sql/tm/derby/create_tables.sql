create table sjta_transactions (
	tid bigint generated always as identity,
	tmid varchar(30),
	formatid int,
	gtid varchar(64) for bit data,
	bqual varchar(64) for bit data,
	state smallint
);
create table sjta_transaction_branches (
	tid bigint,
	bid int,
	formatid int,
	gtid varchar(64) for bit data,
	bqual varchar(64) for bit data,
	state smallint,
	url varchar(128),
	userid varchar(30),
	password varchar(30),
	typeid varchar(30)
);
