create table sjta_transactions (
	tid number,
	tmid varchar2(30),
	formatid number,
	gtid raw(64),
	bqual raw(64),
	state number(2)
);
create table sjta_transaction_branches (
	tid number,
	bid number,
	formatid number,
	gtid raw(64),
	bqual raw(64),
	state number(2),
	url varchar2(128),
	userid varchar2(30),
	password varchar2(30),
	typeid varchar2(30)
);
create sequence sjta_tidseq;
